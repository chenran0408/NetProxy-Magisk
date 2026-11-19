package module

import (
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

var ErrRoutingRuleMoveInvalid = errors.New("移动路由规则必须提供不同的规则配置及各自的 revision 和 content")

type RoutingRuleEdit struct {
	Target   string `json:"target"`
	Revision string `json:"revision"`
	Content  string `json:"content"`
}

func (edit RoutingRuleEdit) valid() bool {
	switch edit.Target {
	case "singbox/route", "singbox/rules/local/proxy.json", "singbox/rules/local/direct.json", "singbox/rules/local/block.json":
		return strings.TrimSpace(edit.Revision) != "" && strings.TrimSpace(edit.Content) != ""
	default:
		return false
	}
}

type stagedConfigFile struct {
	destination string
	candidate   string
}

// ApplyRoutingRuleMove saves the source and destination documents in one
// transaction, with one runtime reload after both replacements.
func ApplyRoutingRuleMove(ctx context.Context, options Options, from, to RoutingRuleEdit) (revisions map[string]string, err error) {
	defer func() { logConfigApply(options, false, err) }()
	if !from.valid() || !to.valid() || from.Target == to.Target {
		return nil, ErrRoutingRuleMoveInvalid
	}
	if err := options.validate(); err != nil {
		return nil, err
	}
	source, err := ResolveConfig(options, from.Target)
	if err != nil {
		return nil, err
	}
	destination, err := ResolveConfig(options, to.Target)
	if err != nil {
		return nil, err
	}
	options, release, err := lockConfigApply(ctx, options, source, false, destination)
	if err != nil {
		return nil, err
	}
	defer release()

	edits := [2]RoutingRuleEdit{from, to}
	files := [2]stagedConfigFile{{destination: source}, {destination: destination}}
	var contents [2][]byte
	revisions = make(map[string]string, len(files))
	for index, edit := range edits {
		current, err := os.ReadFile(files[index].destination)
		if err != nil {
			return nil, err
		}
		content, revision, err := prepareConfigEdit(current, []byte(edit.Content), configSection(edit.Target), edit.Revision)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", edit.Target, err)
		}
		contents[index] = content
		revisions[edit.Target] = revision
	}

	var localRules []stagedConfigFile
	var routeCandidate string
	for index, edit := range edits {
		file := &files[index]
		candidate, err := os.CreateTemp(filepath.Dir(file.destination), ".config-candidate-")
		if err != nil {
			return nil, err
		}
		file.candidate = candidate.Name()
		defer os.Remove(file.candidate)
		_, writeErr := candidate.Write(contents[index])
		closeErr := candidate.Close()
		if writeErr != nil {
			return nil, writeErr
		}
		if closeErr != nil {
			return nil, closeErr
		}
		if edit.Target == "singbox/route" {
			routeCandidate = file.candidate
		} else {
			if err := validateConfig(ctx, options, edit.Target, file.candidate, contents[index]); err != nil {
				return nil, fmt.Errorf("%s: %w", edit.Target, err)
			}
			localRules = append(localRules, *file)
		}
	}
	if routeCandidate != "" {
		if err := validateSingBoxTree(ctx, options, routeCandidate, localRules...); err != nil {
			return nil, fmt.Errorf("singbox/route: %w", err)
		}
	}

	transaction, err := beginConfigApply(options, source, destination)
	if err != nil {
		return nil, err
	}
	for _, file := range files {
		if err := os.Rename(file.candidate, file.destination); err != nil {
			return nil, rollbackConfigApply(options, transaction, err)
		}
	}
	if err := transaction.setPhase("static_replaced"); err != nil {
		return nil, rollbackConfigApply(options, transaction, err)
	}
	if transaction.journal.WasRunning {
		if err := transaction.setPhase("reload_started"); err != nil {
			return nil, rollbackConfigApply(options, transaction, err)
		}
		if err := configReload(ctx, options); err != nil {
			return nil, rollbackConfigApply(options, transaction, fmt.Errorf("路由规则应用失败: %w", err))
		}
	}
	if err := transaction.commit(); err != nil {
		return nil, rollbackAfterCommitFailure(ctx, options, transaction, err)
	}
	return revisions, nil
}
