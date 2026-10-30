package policy

import (
	"context"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/catalog"
)

func TestSaveLoadPreservesSelectionAndAddsNewDefaults(t *testing.T) {
	path := filepath.Join(t.TempDir(), "state", "policies.json")
	items := Defaults()
	items[0].Mode = "manual"
	items[0].NodeTag = "Tokyo"
	if err := Save(context.Background(), path, items); err != nil {
		t.Fatal(err)
	}
	loaded, err := Load(context.Background(), path)
	if err != nil {
		t.Fatal(err)
	}
	if len(loaded) != len(Defaults()) || loaded[0].Mode != "manual" || loaded[0].NodeTag != "Tokyo" {
		t.Fatalf("selection was not preserved: %#v", loaded)
	}
}

func TestLoadMissingReturnsDefaults(t *testing.T) {
	items, err := Load(context.Background(), filepath.Join(t.TempDir(), "missing.json"))
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 6 {
		t.Fatalf("want six defaults, got %d", len(items))
	}
}

func TestDefaultsUseServiceSpecificRuleSets(t *testing.T) {
	defaults := Defaults()
	want := map[string]string{
		"youtube": "geosite/youtube",
		"tiktok":  "geosite/tiktok",
		"netflix": "geosite/netflix",
		"github":  "geosite/github",
	}
	for id, ruleSet := range want {
		item, ok := Find(defaults, id)
		if !ok || len(item.RuleSets) != 1 || item.RuleSets[0] != ruleSet {
			t.Fatalf("%s rule sets = %#v, want [%s]", id, item.RuleSets, ruleSet)
		}
	}
}

func TestSaveUsesPrivateAtomicFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), "policies.json")
	if err := Save(context.Background(), path, Defaults()); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if runtime.GOOS != "windows" && info.Mode().Perm() != 0o600 {
		t.Fatalf("want private policy file, got %o", info.Mode().Perm())
	}
}

func TestBuildRuntimeProjectsAutoManualAndMissingNode(t *testing.T) {
	root := t.TempDir()
	if err := catalog.EnsureGroup(context.Background(), catalog.GroupOptions{Root: root, GroupID: "default", Name: "本地配置", Type: "local"}); err != nil {
		t.Fatal(err)
	}
	if _, err := catalog.AppendNode(context.Background(), catalog.MutationOptions{
		GroupDir: filepath.Join(root, "default"), GroupID: "default", Type: "local",
		Input: "socks://127.0.0.1:1080#" + url.PathEscape("Tokyo"),
	}); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(t.TempDir(), "runtime", "policies.json")
	items := []Policy{
		{ID: "ai", RuleSets: []string{"geosite/ai"}, GroupID: "default", Mode: "auto", RuntimeTag: "Proxy"},
		{ID: "youtube", RuleSets: []string{"geosite/video"}, GroupID: "default", Mode: "manual", NodeTag: "Tokyo", RuntimeTag: "Proxy"},
		{ID: "google", RuleSets: []string{"geosite/google"}, GroupID: "default", Mode: "manual", NodeTag: "Removed", RuntimeTag: "Proxy"},
	}
	written, err := BuildRuntime(context.Background(), path, root, items)
	if err != nil || !written {
		t.Fatalf("BuildRuntime() = %v, %v", written, err)
	}
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	text := string(content)
	if !strings.Contains(text, `"outbound":"Auto/本地配置"`) || !strings.Contains(text, `"outbound":"本地配置/Tokyo"`) {
		t.Fatalf("runtime overlay omitted auto or manual target: %s", text)
	}
	if strings.Contains(text, "Removed") {
		t.Fatalf("runtime overlay retained missing manual node: %s", text)
	}
}
