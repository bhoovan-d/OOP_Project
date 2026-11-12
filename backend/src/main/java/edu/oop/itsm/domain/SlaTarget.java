package edu.oop.itsm.domain;

import edu.oop.itsm.exception.ValidationException;
import jakarta.persistence.Embeddable;

import java.time.Duration;

/** Value object: response + resolution targets for one priority. */
@Embeddable
public class SlaTarget {

    private int responseMinutes;
    private int resolutionMinutes;

    protected SlaTarget() {}

    public SlaTarget(int responseMinutes, int resolutionMinutes) {
        if (responseMinutes <= 0 || resolutionMinutes <= 0) throw new ValidationException("SLA targets must be positive");
        if (resolutionMinutes < responseMinutes) throw new ValidationException("Resolution target cannot be shorter than response target");
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }

    public int getResponseMinutes() { return responseMinutes; }
    public int getResolutionMinutes() { return resolutionMinutes; }
    public Duration response() { return Duration.ofMinutes(responseMinutes); }
    public Duration resolution() { return Duration.ofMinutes(resolutionMinutes); }
}
