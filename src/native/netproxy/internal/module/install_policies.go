package module

import (
	"encoding/json/jsontext"
	json "encoding/json/v2"
	"errors"
	"fmt"
	"os"
	"slices"
)

// MergeInstallPolicies writes a private nodes-mode snapshot from the untouched bundle.
// The installer holds the live configuration lock and publishes the snapshot.
func MergeInstallPolicies(currentPath, bundledPath, outputPath string) error {
	bundled, err := os.ReadFile(bundledPath)
	if err != nil {
		return fmt.Errorf("安装策略合并：读取新版配置: %w", err)
	}
	current, err := os.ReadFile(currentPath)
	merged := bundled
	if err == nil {
		merged, err = mergeInstallPolicyConfig(current, bundled)
		if err != nil {
			return fmt.Errorf("安装策略合并：%w", err)
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("安装策略合并：读取当前配置: %w", err)
	}
	if err := writeConfigAtomic(outputPath, merged, 0o600); err != nil {
		return fmt.Errorf("安装策略合并：写入暂存快照: %w", err)
	}
	return nil
}

func mergeInstallPolicyConfig(current, bundled []byte) ([]byte, error) {
	old, err := configObject(current)
	if err != nil {
		return nil, fmt.Errorf("读取当前主配置: %w", err)
	}
	oldGroups, err := readPolicyReferences(old)
	if err != nil {
		return nil, fmt.Errorf("读取当前出站: %w", err)
	}
	if !slices.ContainsFunc(oldGroups, func(entry policyReferences) bool { return entry.isGroup() && entry.Tag != "" }) {
		return bundled, nil
	}
	defaults, err := configObject(bundled)
	if err != nil {
		return nil, fmt.Errorf("读取新版主配置: %w", err)
	}
	defaultOutbounds, err := readPolicyReferences(defaults)
	if err != nil {
		return nil, fmt.Errorf("读取新版出站: %w", err)
	}
	oldEntries, err := installConfigEntries(old, "outbounds")
	if err != nil {
		return nil, err
	}
	entries, err := installConfigEntries(defaults, "outbounds")
	if err != nil {
		return nil, err
	}
	retained := make(map[string]bool)
	for i, group := range oldGroups {
		if !group.isGroup() || group.Tag == "" {
			continue
		}
		retained[group.Tag] = true
		index := slices.IndexFunc(defaultOutbounds, func(entry policyReferences) bool { return entry.Tag == group.Tag })
		if index == -1 {
			entries = append(entries, oldEntries[i])
			continue
		}
		if !defaultOutbounds[index].isGroup() {
			return nil, fmt.Errorf("策略组 %q 与新版内置出站重名", group.Tag)
		}
		entries[index] = oldEntries[i]
	}
	defaults["outbounds"], err = json.Marshal(entries)
	if err != nil {
		return nil, err
	}
	if err := mergeInstallPolicyRules(old, defaults, retained); err != nil {
		return nil, err
	}
	return json.Marshal(defaults, json.Deterministic(true), jsontext.WithIndent("  "))
}

func mergeInstallPolicyRules(old, defaults map[string]jsontext.Value, retained map[string]bool) error {
	if _, ok := old["route"]; !ok {
		return nil
	}
	oldRoute, err := configObject(old["route"])
	if err != nil {
		return fmt.Errorf("读取当前路由: %w", err)
	}
	oldRules, err := installConfigEntries(oldRoute, "rules")
	if err != nil || len(oldRules) == 0 {
		return err
	}
	defaultRoute, err := configObject(defaults["route"])
	if err != nil {
		return fmt.Errorf("读取新版路由: %w", err)
	}
	rules, err := installConfigEntries(defaultRoute, "rules")
	if err != nil {
		return err
	}
	known := make(map[string]bool)
	for _, rule := range rules {
		key, err := installRuleKey(rule)
		if err != nil {
			return err
		}
		known[key] = true
	}
	var additions []jsontext.Value
	for _, rule := range oldRules {
		tag, err := installEditablePolicyTarget(rule)
		if err != nil {
			return err
		}
		if !retained[tag] {
			continue
		}
		key, err := installRuleKey(rule)
		if err != nil {
			return err
		}
		if !known[key] {
			additions = append(additions, rule)
			known[key] = true
		}
	}
	if len(additions) == 0 {
		return nil
	}
	// Keep the bundle's mode, DNS and local shortcut guards ahead of the
	// retained rules. User rules must precede the default business routes.
	insertAt := len(rules)
	for i, rule := range rules {
		tag, err := installPolicyRuleTarget(rule)
		if err != nil {
			return err
		}
		if tag != "" {
			insertAt = i
			break
		}
	}
	defaultRoute["rules"], err = json.Marshal(slices.Insert(rules, insertAt, additions...))
	if err != nil {
		return err
	}
	defaults["route"], err = json.Marshal(defaultRoute, json.Deterministic(true))
	return err
}

func installEditablePolicyTarget(raw jsontext.Value) (string, error) {
	rule, err := configObject(raw)
	if err != nil {
		return "", err
	}
	// Match the App's explicit, single-field policy rule format. Bundled
	// business routes omit type, and complex rules belong to the JSON editor.
	if len(rule) != 4 || policyString(rule["type"]) != "default" || policyString(rule["action"]) != "route" {
		return "", nil
	}
	for _, field := range []string{"domain_suffix", "domain", "domain_keyword", "domain_regex", "ip_cidr", "port", "port_range", "package_name"} {
		if _, ok := rule[field]; ok {
			return policyString(rule["outbound"]), nil
		}
	}
	return "", nil
}

func installPolicyRuleTarget(raw jsontext.Value) (string, error) {
	rule, err := configObject(raw)
	if err != nil {
		return "", err
	}
	action := policyString(rule["action"])
	if _, mode := rule["clash_mode"]; mode || (action != "" && action != "route") {
		return "", nil
	}
	isShortcut := func(tag string) bool { return tag == "proxy" || tag == "direct" || tag == "block" }
	sets := rule["rule_set"]
	if sets.Kind() == '[' {
		var tags []string
		if err := json.Unmarshal(sets, &tags); err != nil {
			return "", err
		}
		if slices.ContainsFunc(tags, isShortcut) {
			return "", nil
		}
	} else if isShortcut(policyString(sets)) {
		return "", nil
	}
	return policyString(rule["outbound"]), nil
}

func installRuleKey(rule jsontext.Value) (string, error) {
	key := rule.Clone()
	err := key.Format(jsontext.ReorderRawObjects(true))
	return string(key), err
}

func installConfigEntries(config map[string]jsontext.Value, field string) ([]jsontext.Value, error) {
	raw, ok := config[field]
	if !ok {
		return nil, nil
	}
	var entries []jsontext.Value
	if err := json.Unmarshal(raw, &entries); err != nil {
		return nil, fmt.Errorf("读取 %s: %w", field, err)
	}
	return entries, nil
}
