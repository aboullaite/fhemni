package dev.maboullaite.fhemni.programme.media;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import dev.maboullaite.fhemni.programme.media.GeminiProgrammeTtsGateway.PcmAudio;

final class WavePcm {

    private WavePcm() {
    }

    static CombinedAudio combine(List<PcmAudio> segments, int pauseMs) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("At least one narration segment is required.");
        }
        PcmAudio format = segments.getFirst();
        int bytesPerSample = format.bitsPerSample() / 8;
        int pauseBytes = Math.toIntExact((long) format.sampleRate() * format.channels() * bytesPerSample * pauseMs / 1_000);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        long cursorMs = 0;
        List<Timing> timings = new java.util.ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            PcmAudio segment = segments.get(index);
            if (segment.sampleRate() != format.sampleRate()
                    || segment.channels() != format.channels()
                    || segment.bitsPerSample() != format.bitsPerSample()) {
                throw new IllegalArgumentException("All narration segments must use the same PCM format.");
            }
            long startMs = cursorMs;
            try {
                pcm.write(segment.data());
                cursorMs += segment.durationMs();
                timings.add(new Timing(startMs, cursorMs));
                if (index + 1 < segments.size() && pauseBytes > 0) {
                    pcm.write(new byte[pauseBytes]);
                    cursorMs += pauseMs;
                }
            } catch (IOException impossible) {
                throw new IllegalStateException("Narration audio could not be assembled.", impossible);
            }
        }
        return new CombinedAudio(wav(pcm.toByteArray(), format), timings, cursorMs);
    }

    private static byte[] wav(byte[] pcm, PcmAudio format) {
        int byteRate = format.sampleRate() * format.channels() * format.bitsPerSample() / 8;
        int blockAlign = format.channels() * format.bitsPerSample() / 8;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(36 + pcm.length);
        header.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) format.channels());
        header.putInt(format.sampleRate());
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) format.bitsPerSample());
        header.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        header.putInt(pcm.length);
        byte[] result = new byte[44 + pcm.length];
        System.arraycopy(header.array(), 0, result, 0, 44);
        System.arraycopy(pcm, 0, result, 44, pcm.length);
        return result;
    }

    record Timing(long startMs, long endMs) {
    }

    record CombinedAudio(byte[] wav, List<Timing> timings, long durationMs) {
    }
}
