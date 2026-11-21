package com.ecotrace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pickup_item")
public class PickupItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_id")
    private Pickup pickup;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 20)
    private String itemCondition;

    @Column(length = 200)
    private String details;

    protected PickupItem() {
    }

    public PickupItem(String category, int quantity, String itemCondition, String details) {
        this.category = category;
        this.quantity = quantity;
        this.itemCondition = itemCondition;
        this.details = details;
    }

    void setPickup(Pickup pickup) { this.pickup = pickup; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public String getItemCondition() { return itemCondition; }
    public String getDetails() { return details; }
}
