package io.princeofspace.benchmark;

import com.diffplug.spotless.Provisioner;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves Maven coordinates to local files by downloading from Maven Central (direct artifacts only,
 * no transitive resolution). Used by the Spring corpus benchmark for Eclipse JDT / Equo P2 legs.
 */
final class HttpMavenProvisioner implements Provisioner {

    private static final String CENTRAL_PREFIX = "https://repo1.maven.org/maven2/";

    private final Path cacheRoot;

    HttpMavenProvisioner(Path cacheRoot) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot);
    }

    @Override
    public Set<File> provisionWithTransitives(boolean withTransitives, Collection<String> mavenCoordinates) {
        if (withTransitives) {
            throw new UnsupportedOperationException("Transitive resolution is not supported.");
        }
        LinkedHashSet<File> jars = new LinkedHashSet<>();
        for (String coord : mavenCoordinates) {
            jars.add(resolve(coord.strip()));
        }
        return jars;
    }

    private File resolve(String coord) {
        try {
            DefaultArtifact artifact = new DefaultArtifact(coord);
            String fileName =
                    artifact.getArtifactId()
                            + "-"
                            + artifact.getVersion()
                            + (artifact.getClassifier().isEmpty() ? "" : "-" + artifact.getClassifier())
                            + "."
                            + artifact.getExtension();
            Path relative =
                    Path.of(artifact.getGroupId().replace('.', '/'), artifact.getArtifactId(), artifact.getVersion())
                            .resolve(fileName);
            Path target = cacheRoot.resolve(relative);
            if (Files.isRegularFile(target)) {
                return target.toFile();
            }
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "download-", ".tmp");
            try {
                URI uri = URI.create(CENTRAL_PREFIX + relative.toString().replace('\\', '/'));
                try (InputStream in = uri.toURL().openStream();
                        OutputStream out = Files.newOutputStream(temp)) {
                    in.transferTo(out);
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(temp);
                throw e;
            }
            return target.toFile();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve Maven coordinate: " + coord, e);
        }
    }
}
