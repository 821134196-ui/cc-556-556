package com.example.hk.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** CUSTOMER / WORKER / STAFF */
    @Column(nullable = false)
    private String role;

    private String phone;

    protected UserEntity() {
    }

    public UserEntity(String name, String role, String phone) {
        this.name = name;
        this.role = role;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getPhone() { return phone; }
}
