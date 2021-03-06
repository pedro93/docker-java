package com.github.dockerjava.cmd;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.RemoteApiVersion;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.utils.RegistryUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.github.dockerjava.utils.TestUtils.getVersion;
import static com.github.dockerjava.utils.TestUtils.isNotSwarm;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

public class PullImageCmdIT extends CmdIT {
    private static final Logger LOG = LoggerFactory.getLogger(PullImageCmdIT.class);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testPullImage() throws Exception {
        Info info = dockerRule.getClient().infoCmd().exec();
        LOG.info("Client info: {}", info.toString());

        int imgCount = info.getImages();
        LOG.info("imgCount1: {}", imgCount);

        // This should be an image that is not used by other repositories
        // already
        // pulled down, preferably small in size. If tag is not used pull will
        // download all images in that repository but tmpImgs will only
        // deleted 'latest' image but not images with other tags
        String testImage = "hackmann/empty";

        LOG.info("Removing image: {}", testImage);

        try {
            dockerRule.getClient().removeImageCmd(testImage).withForce(true).exec();
        } catch (NotFoundException e) {
            // just ignore if not exist
        }

        info = dockerRule.getClient().infoCmd().exec();
        LOG.info("Client info: {}", info.toString());

        imgCount = info.getImages();
        LOG.info("imgCount2: {}", imgCount);

        LOG.info("Pulling image: {}", testImage);

        dockerRule.getClient().pullImageCmd(testImage)
                .exec(new PullImageResultCallback())
                .awaitCompletion(30, TimeUnit.SECONDS);

        info = dockerRule.getClient().infoCmd().exec();
        LOG.info("Client info after pull, {}", info.toString());

        assertThat(imgCount, lessThanOrEqualTo(info.getImages()));

        InspectImageResponse inspectImageResponse = dockerRule.getClient().inspectImageCmd(testImage).exec();
        LOG.info("Image Inspect: {}", inspectImageResponse.toString());
        assertThat(inspectImageResponse, notNullValue());
    }

    @Test
    public void testPullNonExistingImage() throws Exception {
        if (isNotSwarm(dockerRule.getClient()) && getVersion(dockerRule.getClient())
                .isGreaterOrEqual(RemoteApiVersion.VERSION_1_26)) {
            exception.expect(NotFoundException.class);
        } else {
            exception.expect(DockerClientException.class);
        }

        // stream needs to be fully read in order to close the underlying connection
        dockerRule.getClient().pullImageCmd("xvxcv/foo")
                .exec(new PullImageResultCallback())
                .awaitCompletion(30, TimeUnit.SECONDS);
    }

    @Test
    public void testPullImageWithValidAuth() throws Exception {
        AuthConfig authConfig = RegistryUtils.runPrivateRegistry(dockerRule.getClient());

        String imgName = RegistryUtils.createPrivateImage(dockerRule, "pull-image-with-valid-auth");

        // stream needs to be fully read in order to close the underlying connection
        dockerRule.getClient().pullImageCmd(imgName)
                .withAuthConfig(authConfig)
                .exec(new PullImageResultCallback())
                .awaitCompletion(30, TimeUnit.SECONDS);
    }

    @Test
    public void testPullImageWithValidAuthAndEmail() throws Exception {
        AuthConfig authConfig = RegistryUtils.runPrivateRegistry(dockerRule.getClient())
                .withEmail("foo@bar.de");

        String imgName = RegistryUtils.createPrivateImage(dockerRule, "pull-image-with-valid-auth");

        // stream needs to be fully read in order to close the underlying connection
        dockerRule.getClient().pullImageCmd(imgName)
                .withAuthConfig(authConfig)
                .exec(new PullImageResultCallback())
                .awaitCompletion(30, TimeUnit.SECONDS);
    }

    @Test
    public void testPullImageWithNoAuth() throws Exception {
        RegistryUtils.runPrivateRegistry(dockerRule.getClient());

        String imgName = RegistryUtils.createPrivateImage(dockerRule, "pull-image-with-no-auth");

        if (isNotSwarm(dockerRule.getClient()) && getVersion(dockerRule.getClient())
                .isGreaterOrEqual(RemoteApiVersion.VERSION_1_30)) {
            exception.expect(InternalServerErrorException.class);
        } else {
            exception.expect(DockerClientException.class);
        }

        // stream needs to be fully read in order to close the underlying connection
        dockerRule.getClient().pullImageCmd(imgName)
                .exec(new PullImageResultCallback())
                .awaitCompletion(30, TimeUnit.SECONDS);
    }


    @Test
    public void testPullImageWithInvalidAuth() throws Exception {
        AuthConfig validAuthConfig = RegistryUtils.runPrivateRegistry(dockerRule.getClient());

        AuthConfig authConfig = new AuthConfig()
                .withUsername("testuser")
                .withPassword("testwrongpassword")
                .withEmail("foo@bar.de")
                .withRegistryAddress(validAuthConfig.getRegistryAddress());

        String imgName = RegistryUtils.createPrivateImage(dockerRule, "pull-image-with-invalid-auth");

        if (isNotSwarm(dockerRule.getClient()) && getVersion(dockerRule.getClient())
                .isGreaterOrEqual(RemoteApiVersion.VERSION_1_30)) {
            exception.expect(InternalServerErrorException.class);
        } else {
            exception.expect(DockerClientException.class);
        }

        // stream needs to be fully read in order to close the underlying connection
        dockerRule.getClient().pullImageCmd(imgName)
                .withAuthConfig(authConfig)
                .exec(new PullImageResultCallback())
                .awaitCompletion(30, TimeUnit.SECONDS);
    }
}
