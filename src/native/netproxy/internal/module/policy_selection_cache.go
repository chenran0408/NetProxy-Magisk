package module

import (
	json "encoding/json/v2"
	"errors"
	"os"
	"path/filepath"
	"time"

	"github.com/sagernet/bbolt"
)

type policySelectionCache struct {
	Enabled bool   `json:"enabled"`
	Path    string `json:"path"`
	ID      string `json:"cache_id"`
}

func readPolicySelectionCache(content []byte, dir string) (policySelectionCache, error) {
	var config struct {
		Experimental struct {
			Cache policySelectionCache `json:"cache_file"`
		} `json:"experimental"`
	}
	if err := json.Unmarshal(content, &config); err != nil {
		return policySelectionCache{}, err
	}
	cache := config.Experimental.Cache
	if !cache.Enabled {
		return policySelectionCache{}, nil
	}
	if cache.Path == "" {
		cache.Path = "cache.db"
	}
	if !filepath.IsAbs(cache.Path) {
		cache.Path = filepath.Join(dir, cache.Path)
	}
	cache.Path = filepath.Clean(cache.Path)
	return cache, nil
}

func (c policySelectionCache) renameSelection(db *bbolt.DB, rename PolicyGroupRename) error {
	return db.Update(func(tx *bbolt.Tx) error {
		bucket := tx.Bucket([]byte("selected"))
		if c.ID != "" {
			namespace := tx.Bucket(append([]byte{0}, []byte(c.ID)...))
			if namespace == nil {
				return nil
			}
			bucket = namespace.Bucket([]byte("selected"))
		}
		if bucket == nil {
			return nil
		}
		from, to := []byte(rename.From), []byte(rename.To)
		selected := bucket.Get(from)
		if len(selected) == 0 {
			if err := bucket.Delete(to); err != nil {
				return err
			}
		} else {
			if err := bucket.Put(to, selected); err != nil {
				return err
			}
			if err := bucket.Delete(from); err != nil {
				return err
			}
		}
		var parents []string
		if err := bucket.ForEach(func(group, member []byte) error {
			if string(member) == rename.From {
				parents = append(parents, string(group))
			}
			return nil
		}); err != nil {
			return err
		}
		for _, group := range parents {
			if err := bucket.Put([]byte(group), to); err != nil {
				return err
			}
		}
		return nil
	})
}

// The cache is owned by sing-box. Stop it before opening the database, and
// journal the original selection cache before changing it.
func (transaction *configApplyTransaction) renamePolicySelection(cache policySelectionCache, rename PolicyGroupRename) (err error) {
	db, err := bbolt.Open(cache.Path, 0o600, &bbolt.Options{Timeout: time.Second})
	if err != nil {
		return err
	}
	defer func() { err = errors.Join(err, db.Close()) }()
	snapshot, err := createConfigSnapshot(transaction.directory, "selector-cache", cache.Path)
	if err != nil {
		return err
	}
	if !snapshot.Exists {
		return os.ErrNotExist
	}
	transaction.journal.SelectionCache = &snapshot
	if err := transaction.writeJournal(); err != nil {
		transaction.journal.SelectionCache = nil
		return err
	}
	return cache.renameSelection(db, rename)
}
