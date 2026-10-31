package catalog

import (
	"context"
	"fmt"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/provider"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	SJSON "github.com/sagernet/sing/common/json"
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
	Landing        []option.Outbound
}

func buildChainRuntime(ctx context.Context, groups []*loadedGroup) (*chainRuntime, error) {
	result := &chainRuntime{}
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
		result.FrontProviders = append(result.FrontProviders, group.RuntimeTag)
		copies, err := assignChainProviderTags(ctx, document, group.RuntimeTag)
		if err != nil {
			return nil, fmt.Errorf("生成分组 %s 的链式落地副本: %w", group.ID, err)
		}
		result.Landing = append(result.Landing, copies...)
	}
	if len(result.Landing) == 0 {
		return nil, nil
	}
	return result, nil
}

func assignChainProviderTags(ctx context.Context, document provider.Document, runtimeTag string) ([]option.Outbound, error) {
	result := make([]option.Outbound, 0, len(document.Outbounds))
	for _, original := range document.Outbounds {
		content, err := SJSON.MarshalContext(provider.Context(ctx), original)
		if err != nil {
			return nil, err
		}
		var copy option.Outbound
		if err := SJSON.UnmarshalContext(provider.Context(ctx), content, &copy); err != nil {
			return nil, err
		}
		copy.Tag = runtimeTag + "/" + original.Tag
		dialer, ok := copy.Options.(option.DialerOptionsWrapper)
		if !ok {
			return nil, fmt.Errorf("节点类型 %q 不支持链式前置", original.Type)
		}
		options := dialer.TakeDialerOptions()
		options.Detour = ChainFrontTag
		dialer.ReplaceDialerOptions(options)
		result = append(result, copy)
	}
	return result, nil
}
