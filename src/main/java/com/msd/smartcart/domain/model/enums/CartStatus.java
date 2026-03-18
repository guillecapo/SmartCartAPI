package com.msd.smartcart.domain.model.enums;

public enum CartStatus {
    ACTIVE,
    ORDERED,
    CANCELLED;

    public boolean canAddItems() {
        return this == ACTIVE;
    }

    public boolean isClosed() {
        return this == ORDERED || this == CANCELLED;
    }
}
