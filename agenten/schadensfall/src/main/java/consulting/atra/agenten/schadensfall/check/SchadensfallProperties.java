package consulting.atra.agenten.schadensfall.check;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "schadensfall")
public record SchadensfallProperties(Duration pollInterval, boolean pollEnabled,
                                        Duration reconcileTimeout, BigDecimal freigabeThreshold) {

    public SchadensfallProperties {
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalStateException("schadensfall.poll-interval fehlt oder ist nicht positiv");
        }
        if (reconcileTimeout == null || reconcileTimeout.isNegative() || reconcileTimeout.isZero()) {
            throw new IllegalStateException("schadensfall.reconcile-timeout fehlt oder ist nicht positiv");
        }
        if (freigabeThreshold == null || freigabeThreshold.signum() <= 0) {
            throw new IllegalStateException("schadensfall.freigabe-threshold fehlt oder ist nicht positiv");
        }
    }
}
