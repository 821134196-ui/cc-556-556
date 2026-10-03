package com.example.hk.web;

import com.example.hk.model.UserEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsersResource {

    @Inject
    EntityManager em;

    @Inject
    Assembler assembler;

    @GET
    public List<Dto.UserDto> list() {
        return em.createQuery("select u from UserEntity u order by u.id", UserEntity.class)
                .getResultList().stream().map(assembler::user).toList();
    }
}
