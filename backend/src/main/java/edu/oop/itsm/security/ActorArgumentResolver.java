package edu.oop.itsm.security;

import edu.oop.itsm.domain.Role;
import edu.oop.itsm.event.Actor;
import edu.oop.itsm.exception.InvalidCredentialsException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Lets controllers declare an {@link Actor} parameter, built from the verified JWT claims (no DB hit). */
@Configuration
public class ActorArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Actor.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request, WebDataBinderFactory binder) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) throw new InvalidCredentialsException("Authentication required");
        return new Actor(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("name"), Role.valueOf(jwt.getClaimAsString("role")));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }
}
