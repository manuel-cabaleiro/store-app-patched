package es.storeapp.web.controller;

import es.storeapp.business.exceptions.InstanceNotFoundException;
import es.storeapp.business.services.UserService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ProfileImageController {

    private final UserService userService;

    public ProfileImageController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(
            value = "/users/{id}/image",
            produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) {
        try {
            byte[] contents = userService.getImage(id);

            if (contents == null) {
                return ResponseEntity.notFound().build();
            }

            BufferedImage image = ImageIO.read(
                    new ByteArrayInputStream(contents));

            if (image == null) {
                return ResponseEntity.notFound().build();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Cache-Control", "no-store")
                    .body(output.toByteArray());

        } catch (InstanceNotFoundException | IOException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
