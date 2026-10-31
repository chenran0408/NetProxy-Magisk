//go:build netproxy_no_telemetry

package telemetry

import (
	"context"
	"time"

	"github.com/Fanju6/NetProxy-Magisk/src/native/netproxy/internal/paths"
)

// Reporter keeps the module interfaces stable while compiling out all
// telemetry state, device inspection, and network upload code.
type Reporter struct{}

var ProjectToken, IngestionHost string

func New(paths.Layout) *Reporter { return nil }

func (r *Reporter) RecordActive(time.Time) (bool, error) { return false, nil }

func (r *Reporter) RecordStart(time.Time, time.Duration, string, bool) error { return nil }

func (r *Reporter) Notify() {}

func (r *Reporter) Run(context.Context, func() bool, func()) {}
