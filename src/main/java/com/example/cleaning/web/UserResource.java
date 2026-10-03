package com.example.cleaning.web;

import com.example.cleaning.entity.User;
import com.example.cleaning.model.Role;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

    public record UserDto(Long id, String name, String role, String roleLabel) {}

    @GET
    public List<UserDto> list() {
        return User.<User>listAll().stream()
                .map(u -> new UserDto(u.id, u.name, u.role.name(), u.role.label))
                .toList();
    }

    @GET
    @Path("/roles")
    public List<Map<String, String>> roles() {
        return List.of(
                Map.of("name", Role.CUSTOMER.name(), "label", Role.CUSTOMER.label),
                Map.of("name", Role.CLEANER.name(), "label", Role.CLEANER.label),
                Map.of("name", Role.CUSTOMER_SERVICE.name(), "label", Role.CUSTOMER_SERVICE.label)
        );
    }
}
