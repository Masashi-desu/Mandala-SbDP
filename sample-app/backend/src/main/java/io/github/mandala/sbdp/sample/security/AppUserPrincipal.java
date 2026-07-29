package io.github.mandala.sbdp.sample.security;

import io.github.mandala.sbdp.sample.database.entity.UserEntity;
import io.github.mandala.sbdp.sample.domain.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AppUserPrincipal(
        Long id,
        String username,
        String password,
        Role role,
        boolean enabled) implements UserDetails {

    public static AppUserPrincipal from(UserEntity user) {
        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                Role.valueOf(user.getRole()),
                user.isEnabled());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }
}
