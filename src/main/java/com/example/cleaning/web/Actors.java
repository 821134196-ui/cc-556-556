package com.example.cleaning.web;

import com.example.cleaning.entity.User;
import com.example.cleaning.model.Role;

/** 从 X-Actor-Id 头解析当前演示身份并做角色校验 */
public final class Actors {

    private Actors() {}

    public static User current(Long actorId) {
        if (actorId == null) throw ApiException.badRequest("缺少 X-Actor-Id 请求头（请在页面右上角选择身份）");
        User u = User.findById(actorId);
        if (u == null) throw ApiException.forbidden("身份不存在：" + actorId);
        return u;
    }

    public static User requireRole(Long actorId, Role role) {
        User u = current(actorId);
        if (u.role != role)
            throw ApiException.forbidden("仅%s可执行该操作，当前身份为%s（%s）".formatted(role.label, u.name, u.role.label));
        return u;
    }
}
