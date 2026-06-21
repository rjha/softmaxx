package online.softmaxx.xapi.service;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

import online.softmaxx.xapi.service.param.*;
import online.softmaxx.xapi.service.model.*;
import online.softmaxx.xapi.dao.UserDao;


@Path("/user")
@RequestScoped
public class UserService {

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(final NewUserRequestParam param) {

        if (param == null) {
            throw new IllegalArgumentException("Request body cannot be null or empty.");
        }

        final NewUserRequest userModel = NewUserRequest.create(param);
        final String systemUserKey = UserDao.registerNewUser(userModel);
        
        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                    "status", "success",
                    "message", "User created successfully.",
                    "user_key", systemUserKey
                )).build();
    }

}
