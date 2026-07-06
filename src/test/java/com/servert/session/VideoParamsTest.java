package com.servert.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class VideoParamsTest {

    @Test
    void allowed_resolutions_and_framerate_pass() {
        assertThatCode(() -> new VideoParams("1280x720", 30, "H.264").validate()).doesNotThrowAnyException();
        assertThatCode(() -> new VideoParams("1920x1080", 60, "H.264").validate()).doesNotThrowAnyException();
        assertThatCode(() -> new VideoParams("3840x2160", 1, "H.264").validate()).doesNotThrowAnyException();
    }

    @Test
    void unsupported_resolution_rejected() {
        assertThatThrownBy(() -> new VideoParams("640x480", 30, "H.264").validate())
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("resolution");
    }

    @Test
    void framerate_out_of_range_rejected() {
        assertThatThrownBy(() -> new VideoParams("1920x1080", 0, "H.264").validate())
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> new VideoParams("1920x1080", 61, "H.264").validate())
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void width_height_parsed() {
        VideoParams p = new VideoParams("1920x1080", 30, "H.264");
        assertThat(p.width()).isEqualTo(1920);
        assertThat(p.height()).isEqualTo(1080);
    }
}
