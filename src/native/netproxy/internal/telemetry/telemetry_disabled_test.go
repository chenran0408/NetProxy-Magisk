//go:build netproxy_no_telemetry

package telemetry

import (
	"testing"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/paths"
)

func TestDisabledBuildDoesNotCreateReporterOrState(t *testing.T) {
	root := t.TempDir()
	reporter := New(paths.New(root))
	if reporter != nil {
		t.Fatal("no-telemetry build created a reporter")
	}
}
