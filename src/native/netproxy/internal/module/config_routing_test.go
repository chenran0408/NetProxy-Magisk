package module

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"testing"
)

func TestApplyRoutingRuleMove(t *testing.T) {
	const local = "singbox/rules/local/proxy.json"
	const policy = "singbox/route"
	const localBefore = `{"version":1,"rules":[{"domain":"example.com"}]}`
	const localAfter = `{"version":1,"rules":[]}`
	const routeBefore = `{"route":{"rules":[]}}`
	const routeAfter = `{"route":{"rules":[{"type":"default","domain":"example.com","action":"route","outbound":"Work"}]}}`
	options, mainPath, _, runtime := configApplyOptions(t)
	localPath, err := ResolveConfig(options, local)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(filepath.Dir(localPath), 0o700); err != nil {
		t.Fatal(err)
	}
	for path, content := range map[string]string{
		localPath: localBefore,
		mainPath:  `{"outbounds":[{"type":"selector","tag":"Work","outbounds":["direct"]},{"type":"direct","tag":"direct"}],` + routeBefore[1:],
	} {
		if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	read := func(target string) map[string]string {
		t.Helper()
		document, err := ReadConfig(options, target)
		if err != nil {
			t.Fatal(err)
		}
		return document
	}
	initialLocal, initialRoute := read(local), read(policy)
	from := RoutingRuleEdit{Target: local, Revision: initialLocal["revision"], Content: localAfter}
	to := RoutingRuleEdit{Target: policy, Revision: initialRoute["revision"], Content: routeAfter}
	assertInitial := func() {
		t.Helper()
		if read(local)["revision"] != from.Revision || read(policy)["revision"] != to.Revision {
			t.Fatal("失败的移动修改了源规则集或目标配置")
		}
		assertRuntimeContent(t, options, runtime)
	}
	isolateConfigApplyHooks(t, false)

	badFrom, badTo := from, to
	badFrom.Revision, badTo.Revision = "stale-source", "stale-destination"
	for _, edits := range [][2]RoutingRuleEdit{{badFrom, to}, {from, badTo}} {
		if _, err := ApplyRoutingRuleMove(t.Context(), options, edits[0], edits[1]); !errors.Is(err, ErrConfigConflict) {
			t.Fatalf("未拒绝陈旧的 revision: %v", err)
		}
		assertInitial()
	}
	badTo = to
	badTo.Content = `{"route":[],"outbounds":[]}`
	if _, err := ApplyRoutingRuleMove(t.Context(), options, from, badTo); err == nil {
		t.Fatal("接受了不属于 route 的配置字段")
	}
	assertInitial()

	reloadError := errors.New("候选配置重载失败")
	originalStop := configStop
	t.Cleanup(func() { configStop = originalStop })
	configProcessRunning = func(string) bool { return true }
	configStop = func(context.Context, Options) error { return nil }
	reloads := 0
	configReload = func(context.Context, Options) error {
		reloads++
		if read(local)["content"] != localAfter || read(policy)["revision"] == to.Revision {
			t.Fatal("重载前应完成两份配置替换")
		}
		return reloadError
	}
	configRestoreReload = func(context.Context, Options, configApplyJournal) error {
		assertInitial()
		return nil
	}
	if _, err := ApplyRoutingRuleMove(t.Context(), options, from, to); !errors.Is(err, reloadError) {
		t.Fatalf("未返回重载错误: %v", err)
	}
	if reloads != 1 {
		t.Fatalf("一次移动应只触发一次候选重载: %d", reloads)
	}
	assertInitial()

	configProcessRunning = func(string) bool { return false }
	result, err := ApplyRoutingRuleMove(t.Context(), options, from, to)
	if err != nil {
		t.Fatal(err)
	}
	for _, edit := range []RoutingRuleEdit{from, to} {
		if result[edit.Target] == edit.Revision || result[edit.Target] != read(edit.Target)["revision"] {
			t.Fatalf("移动未返回 %s 的实际 revision: %v", edit.Target, result)
		}
	}
	if _, err := ApplyRoutingRuleMove(t.Context(), options,
		RoutingRuleEdit{Target: policy, Revision: result[policy], Content: routeBefore},
		RoutingRuleEdit{Target: local, Revision: result[local], Content: localBefore},
	); err != nil {
		t.Fatalf("从策略组移回本地规则集失败: %v", err)
	}
	assertInitial()
}
