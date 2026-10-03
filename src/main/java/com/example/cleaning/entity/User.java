package com.example.cleaning.entity;

import com.example.cleaning.model.Role;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

/** 演示用本地账户：客户 / 钟点工 / 客服，用 X-Actor-Id 头切换身份 */
@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public Role role;

    public static User findByName(String name) {
        return find("name", name).firstResult();
    }
}
