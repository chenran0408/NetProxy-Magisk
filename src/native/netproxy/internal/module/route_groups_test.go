package module

import (
	"errors"
	"os"
	"strings"
	"testing"
)

func TestValidateRemovedPolicyGroupsIdentifiesTheReference(t *testing.T) {
	const group = `{"type":"selector","tag":"Work"}`
	const direct = `{"type":"direct","tag":"direct"}`
	for _, test := range []struct {
		name, routing, parent, message string
	}{
		{"rule set", `,"route":{"rules":[{"rule_set":"geosite/bilibili","outbound":"Work"}]}`, "", "geosite/bilibili"},
		{"nested rule", `,"route":{"rules":[{"type":"logical","rules":[{"outbound":"Work"}]}]}`, "", "route.rules[0].rules[0].outbound"},
		{"default outbound", `,"route":{"final":"Work"}`, "", "route.final"},
		{"group member", "", `,{"type":"selector","tag":"Proxy","outbounds":["Work"]}`, `策略组 "Proxy" 的出站成员`},
		{"group default", "", `,{"type":"selector","tag":"Proxy","outbounds":["direct"],"default":"Work"}`, `策略组 "Proxy" 的默认成员`},
		{"unrelated text", `,"route":{"final":"direct","rules":[{"domain":"Work","outbound":"direct"}]}`, "", ""},
	} {
		t.Run(test.name, func(t *testing.T) {
			existing := []byte("{\"outbounds\":[" + direct + test.parent + "," + group + "]" + test.routing + "}")
			candidate := []byte("{\"outbounds\":[" + direct + test.parent + "]" + test.routing + "}")
			err := validateRemovedPolicyGroups(existing, candidate)
			if test.message == "" {
				if err != nil {
					t.Fatal(err)
				}
			} else if err == nil || !strings.Contains(err.Error(), test.message) {
				t.Fatalf("expected %q, got %v", test.message, err)
			}
		})
	}
}

func TestRenamePolicyGroupRewritesReferencesButPreservesOtherTagsAndStrings(t *testing.T) {
	const previous = `{"outbounds":[{"type":"selector","tag":"Work"}]}`
	const candidate = `{
		"outbounds":[{"type":"selector","tag":"Japan"},{"type":"selector","tag":"Proxy","outbounds":["Work"],"default":"Work"},{"type":"socks","tag":"Nested","detour":"Work"}],
		"route":{"final":"Work","rules":[{"type":"logical","rules":[{"package_name":["com.mountains.hills"],"outbound":"Work"}]}]},
		"dns":{"final":"Work","rules":[{"outbound":["Work","Other"]}]},
		"inbounds":[{"type":"mixed","tag":"Another","detour":"Work"}],
		"http_clients":[{"tag":"test","detour":"Work","headers":{"detour":"Work"}}],
		"experimental":{"v2ray_api":{"stats":{"outbounds":["Work"],"inbounds":["Work"]}}},
		"log":{"output":"Work","unused":9007199254740993}
	}`
	updated, err := renamePolicyGroupReferences([]byte(previous), []byte(candidate), PolicyGroupRename{From: "Work", To: "Japan"})
	if err != nil {
		t.Fatal(err)
	}
	config, err := configObject(updated)
	if err != nil {
		t.Fatal(err)
	}
	for section, expected := range map[string][]string{
		"outbounds":    {`"outbounds": ["Japan"]`, `"default": "Japan"`, `"detour": "Japan"`},
		"route":        {`"final": "Japan"`, `"outbound": "Japan"`, `"package_name": ["com.mountains.hills"]`},
		"dns":          {`"final": "Work"`, `"outbound": ["Japan","Other"]`},
		"inbounds":     {`"tag": "Another"`, `"detour": "Work"`},
		"http_clients": {`"detour": "Japan"`, `"headers":`, `"detour": "Work"`},
		"experimental": {`"outbounds": ["Japan"]`, `"inbounds": ["Work"]`},
		"log":          {`9007199254740993`},
	} {
		value := config[section]
		if err := value.Compact(); err != nil {
			t.Fatal(err)
		}
		for _, field := range expected {
			if !strings.Contains(string(value), strings.ReplaceAll(field, " ", "")) {
				t.Errorf("%s missing %s: %s", section, field, config[section])
			}
		}
	}
	if err := validateRemovedPolicyGroups([]byte(previous), updated); err != nil {
		t.Fatal(err)
	}
}

func TestApplyPolicyGroupRenameCommitsDefinitionsAndRoutesTogether(t *testing.T) {
	options, destination, _, _ := configApplyOptions(t)
	isolateConfigApplyHooks(t, false)
	const target = "singbox/config.json"
	const initial = `{"outbounds":[{"type":"direct","tag":"direct"},{"type":"selector","tag":"Work","outbounds":["direct"]},
		{"type":"selector","tag":"Proxy","outbounds":["Work","direct"],"default":"Work"}],
		"route":{"final":"Proxy","rules":[{"type":"default","action":"route","package_name":["com.mountains.hills"],"outbound":"Work"}]}}`
	if err := os.WriteFile(destination, []byte(initial), 0o600); err != nil {
		t.Fatal(err)
	}
	replacement := strings.Replace(initial, `"tag":"Work"`, `"tag":"Japan"`, 1)
	rename := &PolicyGroupRename{From: "Work", To: "Japan"}
	revision, err := ApplyConfigEdit(t.Context(), options, target, []byte(replacement), false, configRevision([]byte(initial)), rename)
	if err != nil {
		t.Fatal(err)
	}
	saved, err := ReadConfig(options, target)
	if err != nil {
		t.Fatal(err)
	}
	content := saved["content"]
	if saved["revision"] != revision || strings.Contains(content, `"Work"`) ||
		!strings.Contains(content, `"outbound": "Japan"`) || !strings.Contains(content, `"default": "Japan"`) {
		t.Fatalf("rename did not persist the complete update: %s", content)
	}
	if _, err := ApplyConfigEdit(t.Context(), options, target, []byte(replacement), false, configRevision([]byte(initial)), rename); !errors.Is(err, ErrConfigConflict) {
		t.Fatalf("expected stale edit conflict, got %v", err)
	}
}
