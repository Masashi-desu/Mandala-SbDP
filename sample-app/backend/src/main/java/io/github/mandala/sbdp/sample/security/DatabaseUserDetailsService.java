package io.github.mandala.sbdp.sample.security;

import io.github.mandala.sbdp.sample.database.dao.UserDao;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserDao userDao;

    public DatabaseUserDetailsService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userDao.selectByUsername(username)
                .map(AppUserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown username"));
    }
}
