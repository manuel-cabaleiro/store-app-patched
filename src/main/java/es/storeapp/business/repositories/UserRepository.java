package es.storeapp.business.repositories;

import es.storeapp.business.entities.User;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository extends AbstractRepository<User> {

    private static final String FIND_USER_BY_EMAIL_QUERY = "SELECT u FROM User u WHERE u.email = :email";
    private static final String COUNT_USER_BY_EMAIL_QUERY = "SELECT COUNT(*) FROM User u WHERE u.email = :email";
    private static final String LOGIN_QUERY = "SELECT u FROM User u WHERE u.email = :email AND u.password = :password";

    public User findByEmail(String email) {
        try {
            Query query = entityManager.createQuery(FIND_USER_BY_EMAIL_QUERY);
            query.setParameter("email", email);
            return (User) query.getSingleResult();
        } catch (NoResultException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public boolean existsUser(String email) {
        Query query = entityManager.createQuery(COUNT_USER_BY_EMAIL_QUERY);
        query.setParameter("email", email);
        return ((Long) query.getSingleResult() > 0);
    }

    public User findByEmailAndPassword(String email, String password) {
        try {
            Query query = entityManager.createQuery(LOGIN_QUERY);
            query.setParameter("email", email);
            query.setParameter("password", password);
            return (User) query.getSingleResult();
        } catch (NoResultException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public void updateSavedCardLastFour(Long userId, String lastFour) {
        entityManager.createNativeQuery("UPDATE users SET card_last_four = :lastFour WHERE user_id = :id")
                .setParameter("lastFour", lastFour)
                .setParameter("id", userId)
                .executeUpdate();
    }

    public User findByRememberToken(String tokenHash, long now) {
        return (User) entityManager.createNativeQuery(
                "SELECT * FROM users WHERE remember_token_hash = :hash AND remember_expires_at > :now",
                User.class)
                .setParameter("hash", tokenHash)
                .setParameter("now", now)
                .getResultStream().findFirst().orElse(null);
    }

    public boolean saveRememberToken(User user, String tokenHash, long expiresAt) {
        return entityManager.createNativeQuery(
                "UPDATE users SET remember_token_hash = :hash, remember_expires_at = :expiry "
                + "WHERE user_id = :id AND password = :password")
                .setParameter("hash", tokenHash)
                .setParameter("expiry", expiresAt)
                .setParameter("id", user.getUserId())
                .setParameter("password", user.getPassword())
                .executeUpdate() == 1;
    }

    public void revokeRememberToken(String tokenHash) {
        entityManager.createNativeQuery(
                "UPDATE users SET remember_token_hash = NULL, remember_expires_at = NULL "
                + "WHERE remember_token_hash = :hash")
                .setParameter("hash", tokenHash)
                .executeUpdate();
    }

    public void revokeRememberTokensForUser(Long userId) {
        entityManager.createNativeQuery(
                "UPDATE users SET remember_token_hash = NULL, remember_expires_at = NULL WHERE user_id = :id")
                .setParameter("id", userId)
                .executeUpdate();
    }
}
