package com.khmori.kagura.dto;

import java.util.List;

public class SyncRequest {
    public String provider;
    public String providerUserId;
    public List<IncomingNote> notes;
}
