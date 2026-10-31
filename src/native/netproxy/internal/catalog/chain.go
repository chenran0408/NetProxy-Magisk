package catalog

import (
	"context"
	"fmt"
	"os"
	"strings"

	"encoding/json/jsontext"
	json "encoding/json/v2"
	"errors"
	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/provider"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"path/filepath"
)

// These tags are intentionally stable: sing-box cache_file uses them to keep
// the front and landing choices across a runtime regeneration.
const (
	ChainFrontTag           = "链式前置"
	ChainOutboundTag        = "链式代理"
	ChainLandingProviderTag = "链式落地"
)

var chainLandingTypes = map[string]struct{}{
	C.TypeHTTP: {}, C.TypeSOCKS: {}, C.TypeShadowsocks: {}, C.TypeVMess: {},
	C.TypeVLESS: {}, C.TypeTrojan: {}, C.TypeAnyTLS: {}, C.TypeSnell: {},
	C.TypeShadowTLS: {}, C.TypeHysteria: {}, C.TypeHysteria2: {}, C.TypeTUIC: {},
}

// ChainProviderEligible restricts landing copies to protocols that support a
// dialer detour. Endpoint groups and mixed groups are left untouched so a
// WireGuard/Tailscale or unsupported outbound cannot make a partial chain.
func ChainProviderEligible(document provider.Document) bool {
	if len(document.Endpoints) != 0 || len(document.Outbounds) == 0 {
		return false
	}
	for _, outbound := range document.Outbounds {
		if _, supported := chainLandingTypes[outbound.Type]; !supported {
			return false
		}
		if _, supported := outbound.Options.(option.DialerOptionsWrapper); !supported {
			return false
		}
	}
	return true
}

type chainRuntime struct {
	FrontProviders []string
	LandingPath    string
	LandingTags    []string
}

func buildChainRuntime(ctx context.Context, groups []*loadedGroup, landingOutput string) (*chainRuntime, error) {
	result := &chainRuntime{}
	copies := make([]map[string]jsontext.Value, 0)
	for _, group := range groups {
		document, err := provider.Load(ctx, group.ProviderPath)
		if err != nil {
			// Runtime generation historically validates only the active target.
			// A broken inactive provider must not prevent ordinary proxy use.
			continue
		}
		if !ChainProviderEligible(document) {
			continue
		}
		eligibleTags, err := eligibleLandingTags(document)
		if err != nil {
			return nil, err
		}
		groupCopies, err := chainLandingCopies(group.ProviderPath, group.RuntimeTag, eligibleTags)
		if err != nil {
			return nil, fmt.Errorf("生成分组 %s 的链式落地副本: %w", group.ID, err)
		}
		if len(groupCopies) == 0 {
			continue
		}
		result.FrontProviders = append(result.FrontProviders, group.RuntimeTag)
		copies = append(copies, groupCopies...)
	}
	if len(copies) == 0 {
		return nil, nil
	}
	if err := writeChainLandingFile(landingOutput, copies); err != nil {
		return nil, err
	}
	result.LandingPath = landingOutput
	for _, copyEntry := range copies {
		var tag string
		if err := json.Unmarshal(copyEntry["tag"], &tag); err != nil {
			return nil, fmt.Errorf("读取链式落地副本标签: %w", err)
		}
		result.LandingTags = append(result.LandingTags, tag)
	}
	return result, nil
}

func eligibleLandingTags(document provider.Document) (map[string]struct{}, error) {
	eligible := make(map[string]struct{}, len(document.Outbounds))
	for _, outbound := range document.Outbounds {
		if _, supported := chainLandingTypes[outbound.Type]; !supported {
			return nil, fmt.Errorf("节点类型 %q 不支持链式前置", outbound.Type)
		}
		if _, supported := outbound.Options.(option.DialerOptionsWrapper); !supported {
			return nil, fmt.Errorf("节点类型 %q 不支持链式前置", outbound.Type)
		}
		if !chainSelectableTag(outbound.Tag) {
			continue
		}
		eligible[outbound.Tag] = struct{}{}
	}
	return eligible, nil
}

// Subscription providers may encode usage and expiry notices as pseudo-node
// outbounds. They are useful metadata in the catalog but cannot be selected as
// a real chain hop because they duplicate another node's connection settings.
func chainSelectableTag(tag string) bool {
	for _, marker := range []string{"剩余流量", "套餐到期", "距离下次重置"} {
		if strings.Contains(tag, marker) {
			return false
		}
	}
	return strings.TrimSpace(tag) != ""
}

// chainLandingCopies 直接复制 Provider 原始 JSON 中的节点对象，只改写
// tag 并追加 detour；不能经过 option 结构体序列化，否则会丢失协议字段。
func chainLandingCopies(providerPath, runtimeTag string, eligible map[string]struct{}) ([]map[string]jsontext.Value, error) {
	content, err := os.ReadFile(providerPath)
	if err != nil {
		return nil, err
	}
	var document struct {
		Outbounds []map[string]jsontext.Value `json:"outbounds"`
	}
	if err := json.Unmarshal(content, &document); err != nil {
		return nil, err
	}
	copies := make([]map[string]jsontext.Value, 0, len(document.Outbounds))
	for _, outbound := range document.Outbounds {
		var originalTag string
		if value, exists := outbound["tag"]; !exists || json.Unmarshal(value, &originalTag) != nil || originalTag == "" {
			return nil, errors.New("节点缺少有效 tag，无法生成链式落地副本")
		}
		if _, ok := eligible[originalTag]; !ok {
			continue
		}
		tagValue, err := json.Marshal(runtimeTag + "/" + originalTag)
		if err != nil {
			return nil, err
		}
		outbound["tag"] = tagValue
		detourValue, err := json.Marshal(ChainFrontTag)
		if err != nil {
			return nil, err
		}
		outbound["detour"] = detourValue
		copies = append(copies, outbound)
	}
	return copies, nil
}

func writeChainLandingFile(path string, outbounds []map[string]jsontext.Value) error {
	return writeJSONAtomic(path, struct {
		Outbounds []map[string]jsontext.Value `json:"outbounds"`
	}{Outbounds: outbounds})
}

func chainLandingOutputPath(providersOutput string) string {
	return filepath.Join(filepath.Dir(providersOutput), "chain-landing.json")
}
