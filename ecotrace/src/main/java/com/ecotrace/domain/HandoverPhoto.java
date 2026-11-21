package com.ecotrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Optional photographic evidence of a handover. At most one photo per handover; stored in the database. */
@Entity
@Table(name = "handover_photo")
public class HandoverPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handover_id", nullable = false, unique = true)
    private Handover handover;

    @Column(nullable = false, length = 30)
    private String contentType;

    @Column(nullable = false)
    private int sizeBytes;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = 3_145_728)
    private byte[] data;

    @Column(nullable = false)
    private Instant uploadedAt;

    protected HandoverPhoto() {
    }

    public HandoverPhoto(Handover handover, String contentType, byte[] data, Instant uploadedAt) {
        this.handover = handover;
        this.contentType = contentType;
        this.data = data.clone();
        this.sizeBytes = data.length;
        this.uploadedAt = uploadedAt;
    }

    public void replace(String contentType, byte[] data, Instant at) {
        this.contentType = contentType;
        this.data = data.clone();
        this.sizeBytes = data.length;
        this.uploadedAt = at;
    }

    public Long getId() { return id; }
    public Handover getHandover() { return handover; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public byte[] getData() { return data.clone(); }
    public Instant getUploadedAt() { return uploadedAt; }
}
