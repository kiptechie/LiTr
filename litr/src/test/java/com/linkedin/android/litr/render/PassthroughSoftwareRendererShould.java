package com.linkedin.android.litr.render;

import android.media.MediaCodec;

import com.linkedin.android.litr.codec.Encoder;
import com.linkedin.android.litr.codec.Frame;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;

import static com.linkedin.android.litr.render.PassthroughSoftwareRenderer.FRAME_WAIT_TIMEOUT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PassthroughSoftwareRendererShould {

    private static final long FRAME_PRESENTATION_TIME = 42L;
    private static final int FRAME_TAG = 1;
    private static final int FRAME_SIZE = 128;
    private static final int FRAME_OFFSET = 0;

    @Mock private Encoder encoder;

    private PassthroughSoftwareRenderer renderer;

    private Frame frame;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        bufferInfo.set(FRAME_OFFSET, FRAME_SIZE, FRAME_PRESENTATION_TIME, 0);

        ByteBuffer inputBuffer = ByteBuffer.allocate(FRAME_SIZE);
        for (int index = 0; index < FRAME_SIZE; index++) {
            inputBuffer.put((byte) index);
        }

        frame = new Frame(
                0,
                inputBuffer,
                bufferInfo);

        renderer = new PassthroughSoftwareRenderer(encoder);
    }

    @Test
    public void notUseInputSurface() {
        assertNull(renderer.getInputSurface());
    }

    @Test
    public void notRenderWhenNoFrameProvided() {
        renderer.renderFrame(null, FRAME_PRESENTATION_TIME);

        verify(encoder, never()).dequeueInputFrame(anyLong());
    }

    @Test
    public void notRenderWhenFrameHasNullBuffer() {
        Frame frame = new Frame(0, null, null);

        renderer.renderFrame(frame, FRAME_PRESENTATION_TIME);

        verify(encoder, never()).dequeueInputFrame(anyLong());
    }

    @Test
    public void dropFrameWhenCannotQueueToEncoder() {
        when(encoder.dequeueInputFrame(FRAME_WAIT_TIMEOUT)).thenReturn(MediaCodec.INFO_TRY_AGAIN_LATER);

        renderer.renderFrame(frame, FRAME_PRESENTATION_TIME);

        verify(encoder, never()).getInputFrame(anyInt());
    }

    @Test
    public void notRenderWhenEncoderReturnsNullInputFrame() {
        when(encoder.dequeueInputFrame(FRAME_WAIT_TIMEOUT)).thenReturn(FRAME_TAG);
        when(encoder.getInputFrame(FRAME_TAG)).thenReturn(null);

        renderer.renderFrame(frame, FRAME_PRESENTATION_TIME);

        verify(encoder, never()).queueInputFrame(frame);
    }

    @Test
    public void renderWhenEncoderAcceptsFrames() {
        int encoderFrameTag = 2;
        ByteBuffer encoderInputBuffer = ByteBuffer.allocate(FRAME_SIZE);
        MediaCodec.BufferInfo encoderBufferInfo = new MediaCodec.BufferInfo();
        Frame encoderInputFrame = new Frame(encoderFrameTag, encoderInputBuffer, encoderBufferInfo);

        when(encoder.dequeueInputFrame(FRAME_WAIT_TIMEOUT)).thenReturn(FRAME_TAG);
        when(encoder.getInputFrame(FRAME_TAG)).thenReturn(encoderInputFrame);

        renderer.renderFrame(frame, FRAME_PRESENTATION_TIME);

        verify(encoder).queueInputFrame(encoderInputFrame);
        assertThat(encoderInputFrame.bufferInfo.flags, is(frame.bufferInfo.flags));
        assertThat(encoderInputFrame.bufferInfo.presentationTimeUs, is(frame.bufferInfo.presentationTimeUs));
        assertThat(encoderInputFrame.bufferInfo.offset, is(0));
        assertThat(encoderInputFrame.bufferInfo.size, is(frame.bufferInfo.size));

        for (int index = 0; index < FRAME_SIZE; index++) {
            assertThat(frame.buffer.get(index), is(encoderInputBuffer.get(index)));
        }
    }
}
