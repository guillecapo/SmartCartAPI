package com.msd.smartcart.domain.port.out;

import java.util.List;

public interface AiRecommenderPort {
    void suggest(String userId, List<String> productIds);
}