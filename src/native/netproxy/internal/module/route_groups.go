package module

import (
	"encoding/json/jsontext"
	json "encoding/json/v2"
	"errors"
	"fmt"
	"maps"
	"slices"
	"strings"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/service"
)

type PolicyGroupRename struct {
	From string `json:"from"`
	To   string `json:"to"`
}

func renamePolicyGroupReferences(current, candidate []byte, rename PolicyGroupRename) ([]byte, error) {
	if rename.From == "" || rename.To == "" || rename.From == rename.To {
		return nil, errors.New("请提供不同的原策略组名称和新名称")
	}
	before, err := configObject(current)
	if err != nil {
		return nil, err
	}
	after, err := configObject(candidate)
	if err != nil {
		return nil, err
	}
	previous, err := readPolicyReferences(before)
	if err != nil {
		return nil, err
	}
	next, err := readPolicyReferences(after)
	if err != nil {
		return nil, err
	}
	isGroup := func(groups []policyReferences, tag string) bool {
		return slices.ContainsFunc(groups, func(group policyReferences) bool { return group.Tag == tag && group.isGroup() })
	}
	if !isGroup(previous, rename.From) || isGroup(previous, rename.To) || isGroup(next, rename.From) || !isGroup(next, rename.To) {
		return nil, errors.New("改名前后的策略组定义不匹配")
	}
	if err := editPolicyReferences(after, func(tag, _ string) (string, error) {
		if tag == rename.From {
			return rename.To, nil
		}
		return tag, nil
	}); err != nil {
		return nil, err
	}
	return json.Marshal(after, json.Deterministic(true), jsontext.WithIndent("  "))
}

// Config check does not resolve every route action against the outbound registry.
func validateRemovedPolicyGroups(current, candidate []byte) error {
	before, err := configObject(current)
	if err != nil {
		return err
	}
	after, err := configObject(candidate)
	if err != nil {
		return err
	}
	previous, err := readPolicyReferences(before)
	if err != nil {
		return err
	}
	next, err := readPolicyReferences(after)
	if err != nil {
		return err
	}
	removed := make(map[string]bool)
	for _, group := range previous {
		if group.isGroup() && group.Tag != "" {
			removed[group.Tag] = true
		}
	}
	for _, outbound := range next {
		delete(removed, outbound.Tag)
	}
	if len(removed) == 0 {
		return nil
	}
	return editPolicyReferences(after, func(tag, location string) (string, error) {
		if removed[tag] {
			return tag, &service.Error{
				Code: "policy.group_in_use", Message: fmt.Sprintf("%s 仍引用策略组 %q，请先修改该引用", location, tag),
			}
		}
		return tag, nil
	})
}

type policyReferenceEdit func(tag, location string) (string, error)

func editPolicyReferences(config map[string]jsontext.Value, edit policyReferenceEdit) error {
	for _, section := range []string{"route", "outbounds", "dns", "http_clients", "endpoints", "providers", "inbounds",
		"services", "ntp", "certificate", "certificate_providers", "experimental"} {
		if value, ok := config[section]; ok {
			updated, err := editPolicyReferenceTree(value, section, edit)
			if err != nil {
				return err
			}
			config[section] = updated
		}
	}
	return nil
}

func editPolicyReferenceTree(value jsontext.Value, path string, edit policyReferenceEdit) (jsontext.Value, error) {
	switch value.Kind() {
	case '{':
		object, err := configObject(value)
		if err != nil {
			return nil, err
		}
		group := policyReferences{Type: policyString(object["type"])}
		for _, key := range slices.Sorted(maps.Keys(object)) {
			if key == "headers" || key == "extra_headers" {
				continue
			}
			field := path + "." + key
			if isPolicyReferenceField(path, key, group.isGroup()) {
				location := field
				if tag := policyString(object["tag"]); group.isGroup() && tag != "" {
					member := "出站成员"
					if key == "default" {
						member = "默认成员"
					}
					location = fmt.Sprintf("策略组 %q 的%s", tag, member)
				} else if key == "outbound" {
					location = policyRuleLocation(field, object)
				}
				object[key], err = editPolicyReferenceValue(object[key], location, edit)
			} else {
				object[key], err = editPolicyReferenceTree(object[key], field, edit)
			}
			if err != nil {
				return nil, err
			}
		}
		return json.Marshal(object, json.Deterministic(true))
	case '[':
		var items []jsontext.Value
		if err := json.Unmarshal(value, &items); err != nil {
			return nil, err
		}
		for i, item := range items {
			updated, err := editPolicyReferenceTree(item, fmt.Sprintf("%s[%d]", path, i), edit)
			if err != nil {
				return nil, err
			}
			items[i] = updated
		}
		return json.Marshal(items, json.Deterministic(true))
	default:
		return value, nil
	}
}

func isPolicyReferenceField(path, key string, group bool) bool {
	switch key {
	case "outbound":
		return strings.HasPrefix(path, "route.rules[") || strings.HasPrefix(path, "dns.rules[")
	case "final":
		return path == "route"
	case "default":
		return group
	case "outbounds":
		return group || path == "experimental.v2ray_api.stats"
	case "detour":
		// A top-level inbound detour targets another inbound. Nested dialers target an outbound.
		return !strings.HasPrefix(path, "inbounds[") || strings.Contains(path, "].")
	case "download_detour", "external_ui_download_detour":
		return true
	default:
		return false
	}
}

func editPolicyReferenceValue(value jsontext.Value, location string, edit policyReferenceEdit) (jsontext.Value, error) {
	if value.Kind() == '[' {
		var tags []string
		if err := json.Unmarshal(value, &tags); err != nil {
			return nil, err
		}
		for i, tag := range tags {
			updated, err := edit(tag, location)
			if err != nil {
				return nil, err
			}
			tags[i] = updated
		}
		return json.Marshal(tags, json.Deterministic(true))
	}
	if tag := policyString(value); tag != "" {
		updated, err := edit(tag, location)
		if err != nil {
			return nil, err
		}
		if tag != updated {
			return json.Marshal(updated, json.Deterministic(true))
		}
	}
	return value, nil
}

func policyString(value jsontext.Value) string {
	if value.Kind() != '"' {
		return ""
	}
	var text string
	_ = json.Unmarshal(value, &text)
	return text
}

func policyRuleLocation(field string, rule map[string]jsontext.Value) string {
	for _, key := range []string{"rule_set", "package_name", "domain", "domain_suffix", "ip_cidr", "port"} {
		if value, ok := rule[key]; ok {
			return fmt.Sprintf("规则 %s（%s %s）", field, key, value)
		}
	}
	return "规则 " + field
}
