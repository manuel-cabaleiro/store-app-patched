package es.storeapp.business.services;

import es.storeapp.business.entities.User;
import es.storeapp.business.exceptions.AuthenticationException;
import es.storeapp.business.exceptions.DuplicatedResourceException;
import es.storeapp.business.exceptions.InstanceNotFoundException;
import es.storeapp.business.exceptions.ServiceException;
import es.storeapp.business.repositories.UserRepository;
import es.storeapp.business.utils.ExceptionGenerationUtils;
import es.storeapp.common.ConfigurationParameters;
import es.storeapp.common.Constants;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.mail.HtmlEmail;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


import javax.imageio.ImageIO;

import static org.springframework.util.StringUtils.hasText;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    ConfigurationParameters configurationParameters;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageSource messageSource;

    @Autowired
    ExceptionGenerationUtils exceptionGenerationUtils;

    private File resourcesDir;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif");


    @PostConstruct
    public void init() {
        resourcesDir = new File(configurationParameters.getResources());
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public String issueRememberToken(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expiry = System.currentTimeMillis() + Constants.REMEMBER_MAX_AGE_SECONDS * 1000L;
        return userRepository.saveRememberToken(user, hashRememberToken(token), expiry) ? token : null;
    }

    @Transactional(readOnly = true)
    public User findByRememberToken(String token) {
        return isRememberToken(token)
                ? userRepository.findByRememberToken(hashRememberToken(token), System.currentTimeMillis())
                : null;
    }

    @Transactional
    public void revokeRememberToken(String token) {
        if (isRememberToken(token)) {
            userRepository.revokeRememberToken(hashRememberToken(token));
        }
    }

    private static boolean isRememberToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private static String hashRememberToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional(readOnly = true)
    public User login(String email, String clearPassword) throws AuthenticationException {
        if (!userRepository.existsUser(email)) {
            throw exceptionGenerationUtils.toAuthenticationException(Constants.AUTH_INVALID_USER_MESSAGE, email);
        }
        User user = userRepository.findByEmail(email);
        if (user == null || !BCrypt.checkpw(clearPassword, user.getPassword())) {
            throw exceptionGenerationUtils.toAuthenticationException(Constants.AUTH_INVALID_PASSWORD_MESSAGE, email);
        }
        return user;
    }

    @Transactional()
    public void sendResetPasswordEmail(String email, String url, Locale locale)
            throws AuthenticationException, ServiceException {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw exceptionGenerationUtils.toAuthenticationException(Constants.AUTH_INVALID_USER_MESSAGE, email);
        }
        String token = UUID.randomUUID().toString();

        try {

            System.setProperty("mail.smtp.ssl.protocols", "TLSv1.2");

            HtmlEmail htmlEmail = new HtmlEmail();
            htmlEmail.setHostName(configurationParameters.getMailHost());
            htmlEmail.setSmtpPort(configurationParameters.getMailPort());
            htmlEmail.setSslSmtpPort(Integer.toString(configurationParameters.getMailPort()));
            htmlEmail.setAuthentication(configurationParameters.getMailUserName(),
                    configurationParameters.getMailPassword());
            htmlEmail.setSSLOnConnect(configurationParameters.getMailSslEnable() != null
                    && configurationParameters.getMailSslEnable());
            if (configurationParameters.getMailStartTlsEnable()) {
                htmlEmail.setStartTLSEnabled(true);
                htmlEmail.setStartTLSRequired(true);
            }
            htmlEmail.addTo(email, user.getName());
            htmlEmail.setFrom(configurationParameters.getMailFrom());
            htmlEmail.setSubject(messageSource.getMessage(Constants.MAIL_SUBJECT_MESSAGE,
                    new Object[]{user.getName()}, locale));

            String link = url + Constants.PARAMS
                    + Constants.TOKEN_PARAM + Constants.PARAM_VALUE + token + Constants.NEW_PARAM_VALUE
                    + Constants.EMAIL_PARAM + Constants.PARAM_VALUE + email;

            htmlEmail.setHtmlMsg(messageSource.getMessage(Constants.MAIL_TEMPLATE_MESSAGE,
                    new Object[]{user.getName(), link}, locale));

            htmlEmail.setTextMsg(messageSource.getMessage(Constants.MAIL_HTML_NOT_SUPPORTED_MESSAGE,
                    new Object[0], locale));

            htmlEmail.send();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            throw new ServiceException(ex.getMessage());
        }

        user.setResetPasswordToken(token);
        userRepository.update(user);
    }

    @Transactional
    public User create(String name, String email, String password, String address,
            String image, byte[] imageContents) throws DuplicatedResourceException, ServiceException {
        if (userRepository.findByEmail(email) != null) {
            throw exceptionGenerationUtils.toDuplicatedResourceException(Constants.EMAIL_FIELD, email,
                    Constants.DUPLICATED_INSTANCE_MESSAGE);
        }
        // Se crea sin imagen: el nombre del cliente no debe llegar a la BD
        User user = userRepository.create(new User(name, email,
                BCrypt.hashpw(password, BCrypt.gensalt()), address, null));

        String savedImage = saveProfileImage(user.getUserId(), image, imageContents);
        if (savedImage != null) {
            user.setImage(savedImage);
            user = userRepository.update(user);
        }
        return user;
    }

    @Transactional
    public User update(Long id, String name, String email, String address, String image, byte[] imageContents)
            throws DuplicatedResourceException, InstanceNotFoundException, ServiceException {
        User user = userRepository.findById(id);
        User emailUser = userRepository.findByEmail(email);
        if (emailUser != null && !Objects.equals(emailUser.getUserId(), user.getUserId())) {
            throw exceptionGenerationUtils.toDuplicatedResourceException(Constants.EMAIL_FIELD, email,
                    Constants.DUPLICATED_INSTANCE_MESSAGE);
        }
        user.setName(name);
        user.setEmail(email);
        user.setAddress(address);
        if (hasText(image) && imageContents != null) {
            String savedImage = saveProfileImage(id, image, imageContents);
            try {
                deleteProfileImage(id, user.getImage());
            } catch (IOException ex) {
                logger.error(ex.getMessage(), ex);
            }
            user.setImage(savedImage);
        }
        return userRepository.update(user);
    }

    @Transactional
    public User changePassword(Long id, String oldPassword, String password)
            throws InstanceNotFoundException, AuthenticationException {
        User user = userRepository.findById(id);
        if (user == null) {
            throw exceptionGenerationUtils.toAuthenticationException(
                    Constants.AUTH_INVALID_USER_MESSAGE, id.toString());
        }
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw exceptionGenerationUtils.toAuthenticationException(Constants.AUTH_INVALID_PASSWORD_MESSAGE,
                    id.toString());
        }
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        userRepository.revokeRememberTokensForUser(user.getUserId());
        return userRepository.update(user);
    }

    @Transactional
    public User changePassword(String email, String password, String token) throws AuthenticationException {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw exceptionGenerationUtils.toAuthenticationException(Constants.AUTH_INVALID_USER_MESSAGE, email);
        }
        if (user.getResetPasswordToken() == null || !user.getResetPasswordToken().equals(token)) {
            throw exceptionGenerationUtils.toAuthenticationException(Constants.AUTH_INVALID_TOKEN_MESSAGE, email);
        }
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
        userRepository.revokeRememberTokensForUser(user.getUserId());
        user.setResetPasswordToken(null);
        return userRepository.update(user);
    }

    @Transactional
    public User removeImage(Long id) throws InstanceNotFoundException, ServiceException {
        User user = userRepository.findById(id);
        try {
            deleteProfileImage(id, user.getImage());
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            throw new ServiceException(ex.getMessage());
        }
        user.setImage(null);
        return userRepository.update(user);
    }

    @Transactional
    public byte[] getImage(Long id) throws InstanceNotFoundException {
        User user = userRepository.findById(id);
        try {
            return getProfileImage(id, user.getImage());
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }

    private String saveProfileImage(Long id, String originalName, byte[] imageContents)
            throws ServiceException {
        if (!hasText(originalName) || imageContents == null) {
            return null;
        }

        // Del nombre del cliente solo se usa la extensión, y solo si está en la lista blanca
        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new ServiceException("Formato de imagen no permitido");
        }
        extension = extension.toLowerCase(Locale.ROOT);
        try {
            // Comprobar que el contenido es realmente una imagen
            if (ImageIO.read(new ByteArrayInputStream(imageContents)) == null) {
                throw new ServiceException("El fichero no es una imagen válida");
            }

            String fileName = UUID.randomUUID() + "." + extension;
            Path target = resolveUserFile(id, fileName);
            Files.createDirectories(target.getParent());
            Files.write(target, imageContents, StandardOpenOption.CREATE_NEW);
            return fileName;
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            throw new ServiceException("No se pudo guardar la imagen");
        }
    }

    private void deleteProfileImage(Long id, String image) throws IOException {
        if (hasText(image)) {
            Files.deleteIfExists(resolveUserFile(id, image));
        }
    }

    private byte[] getProfileImage(Long id, String image) throws IOException {
        if (hasText(image)) {
            return Files.readAllBytes(resolveUserFile(id, image));
        }
        return null;
    }


    /**
     * Construye la ruta de un fichero dentro de la carpeta del usuario y
     * garantiza que, una vez resuelta, no sale de ella.
     */
    private Path resolveUserFile(Long id, String fileName) throws IOException {
    if (!hasText(fileName)
            || fileName.contains("/")
            || fileName.contains("\\")
            || fileName.equals(".")
            || fileName.equals("..")) {
        throw new IOException("Nombre de fichero no válido");
    }

    Path baseDir = resourcesDir.toPath().toRealPath();
    Path userDir = baseDir.resolve(id.toString()).normalize();
    Path target = userDir.resolve(fileName).normalize();

    if (!userDir.startsWith(baseDir)
            || !target.getParent().equals(userDir)) {
        throw new IOException("Ruta de fichero no válida");
    }

    if (Files.isSymbolicLink(userDir)
            || Files.isSymbolicLink(target)) {
        throw new IOException("No se permiten enlaces simbólicos");
    }

    return target;
}
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
