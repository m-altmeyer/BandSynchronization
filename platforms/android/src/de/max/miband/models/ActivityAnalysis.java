package de.max.miband.models;

import java.util.List;

public class ActivityAnalysis {

    public ActivityAmounts calculateActivityAmounts(List<ActivityData> samples) {
        ActivityAmount deepSleep = new ActivityAmount(ActivityKind.TYPE_DEEP_SLEEP);
        ActivityAmount lightSleep = new ActivityAmount(ActivityKind.TYPE_LIGHT_SLEEP);
        ActivityAmount activity = new ActivityAmount(ActivityKind.TYPE_ACTIVITY);

        ActivityAmount previousAmount = null;
        ActivityData previousSample = null;
        for (ActivityData sample : samples) {
            ActivityAmount amount = null;
            switch (sample.getType()) {
                case ActivityData.TYPE_DEEP_SLEEP:
                    amount = deepSleep;
                    break;
                case ActivityData.TYPE_LIGHT_SLEEP:
                    amount = lightSleep;
                    break;
                case ActivityData.TYPE_UNKNOWN:
                default:
                    amount = activity;
                    break;
            }

            if (previousSample != null) {
                long timeDifference = sample.getTimestamp() - previousSample.getTimestamp();
                if (previousSample.getType() == sample.getType()) {
                    amount.addSeconds(timeDifference);
                } else {
                    long sharedTimeDifference = (long) (timeDifference / 2.0f);
                    previousAmount.addSeconds(sharedTimeDifference);
                    amount.addSeconds(sharedTimeDifference);
                }
            } else {
                // nothing to do, we can only calculate when we have the next sample
            }

            previousAmount = amount;
            previousSample = sample;
        }

        ActivityAmounts result = new ActivityAmounts();
        if (deepSleep.getTotalSeconds() > 0) {
            result.addAmount(deepSleep);
        }
        if (lightSleep.getTotalSeconds() > 0) {
            result.addAmount(lightSleep);
        }
        if (activity.getTotalSeconds() > 0) {
            result.addAmount(activity);
        }
        result.calculatePercentages();

        return result;
    }
}
