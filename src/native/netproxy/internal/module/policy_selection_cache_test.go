package module

import (
	"context"
	"errors"
	"maps"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/sagernet/bbolt"
)

func TestPolicyGroupRenamePreservesCachedSelections(t *testing.T) {
	for _, outcome := range []string{"stopped", "running", "rollback"} {
		t.Run(outcome, func(t *testing.T) {
			options, _, _, alive := inboundApplyFixture(t, "ebpf", outcome != "stopped")
			destination, err := ResolveConfig(options, "singbox/config.json")
			if err != nil {
				t.Fatal(err)
			}
			before := []byte(`{"experimental":{"cache_file":{"enabled":true,"path":"cache.db","cache_id":"netproxy"}},
				"outbounds":[{"type":"direct","tag":"direct"},{"type":"block","tag":"block"},
				{"type":"selector","tag":"Work","outbounds":["block","direct"]},
				{"type":"selector","tag":"Proxy","outbounds":["block","Work"]}],
				"route":{"final":"Proxy"}}`)
			if err := os.WriteFile(destination, before, 0o600); err != nil {
				t.Fatal(err)
			}
			candidate := []byte(strings.Replace(string(before), `"tag":"Work"`, `"tag":"Japan"`, 1))
			cachePath := filepath.Join(options.SingBoxDir, "cache.db")
			namespace := []byte("\x00netproxy")
			oldSelections := map[string]string{"Proxy": "Work", "Work": "direct", "Japan": "block", "other": "block"}
			newSelections := map[string]string{"Proxy": "Japan", "Japan": "direct", "other": "block"}
			db, err := bbolt.Open(cachePath, 0o600, nil)
			if err != nil {
				t.Fatal(err)
			}
			err = db.Update(func(tx *bbolt.Tx) error {
				root, err := tx.CreateBucket(namespace)
				if err != nil {
					return err
				}
				selected, err := root.CreateBucket([]byte("selected"))
				if err != nil {
					return err
				}
				for group, member := range oldSelections {
					if err := selected.Put([]byte(group), []byte(member)); err != nil {
						return err
					}
				}
				return nil
			})
			if err := errors.Join(err, db.Close()); err != nil {
				t.Fatal(err)
			}

			assertSelections := func(want map[string]string) {
				t.Helper()
				db, err := bbolt.Open(cachePath, 0o600, &bbolt.Options{ReadOnly: true})
				if err != nil {
					t.Fatal(err)
				}
				got := make(map[string]string)
				err = db.View(func(tx *bbolt.Tx) error {
					return tx.Bucket(namespace).Bucket([]byte("selected")).ForEach(func(k, v []byte) error {
						got[string(k)] = string(v)
						return nil
					})
				})
				if err := errors.Join(err, db.Close()); err != nil {
					t.Fatal(err)
				}
				if !maps.Equal(got, want) {
					t.Fatalf("selections = %v, want %v", got, want)
				}
			}

			startFailure := errors.New("candidate failed to start")
			configStop = func(context.Context, Options) error {
				assertSelections(oldSelections)
				*alive = false
				return nil
			}
			configStart = func(context.Context, Options) error {
				if *alive {
					t.Fatal("replaced the cache while the old core was live")
				}
				assertSelections(newSelections)
				if outcome == "rollback" {
					return startFailure
				}
				*alive = true
				return nil
			}
			configRestoreReload = func(context.Context, Options, configApplyJournal) error {
				assertSelections(oldSelections)
				*alive = true
				return nil
			}
			_, err = ApplyConfigEdit(t.Context(), options, "singbox/config.json", candidate,
				false, configRevision(before), &PolicyGroupRename{From: "Work", To: "Japan"})
			if outcome == "rollback" {
				if !errors.Is(err, startFailure) || !*alive {
					t.Fatalf("running rollback failed: %v", err)
				}
				assertSelections(oldSelections)
				restored, err := os.ReadFile(destination)
				if err != nil || string(restored) != string(before) {
					t.Fatalf("original config was not restored: %v", err)
				}
			} else {
				if err != nil {
					t.Fatal(err)
				}
				assertSelections(newSelections)
			}
			assertInboundJournalAbsent(t, options)
		})
	}
}
