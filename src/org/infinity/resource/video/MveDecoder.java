// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.video;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import javax.sound.sampled.AudioFormat;

import org.infinity.exceptions.UnsupportedFormatException;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.Logger;
import org.infinity.util.io.StreamUtils;

/**
 * Decodes a MVE video resource.
 */
public class MveDecoder {
  // MVE header signature
  public static final String MVE_SIGNATURE = "Interplay MVE File\u001a\u0000\u001a\u0000\u0000\u0001\u0033\u0011";
  public static final String BIK_SIGNATURE = "BIKi";  // IWD2 contains Bink video files with MVE file extension

  // supported MVE chunks
  public static final int MVE_CHUNK_NONE          =     -1;   // used internally
  public static final int MVE_CHUNK_INIT_AUDIO    = 0x0000;
  public static final int MVE_CHUNK_AUDIO_ONLY    = 0x0001;
  public static final int MVE_CHUNK_INIT_VIDEO    = 0x0002;
  public static final int MVE_CHUNK_VIDEO         = 0x0003;
  public static final int MVE_CHUNK_SHUTDOWN      = 0x0004;
  public static final int MVE_CHUNK_END           = 0x0005;

  // supported MVE segments
  public static final int MVE_OC_END_OF_STREAM    = 0x0000;
  public static final int MVE_OC_END_OF_CHUNK     = 0x0001;
  public static final int MVE_OC_CREATE_TIMER     = 0x0002;
  public static final int MVE_OC_AUDIO_BUFFERS    = 0x0003;
  public static final int MVE_OC_PLAY_AUDIO       = 0x0004;
  public static final int MVE_OC_VIDEO_BUFFERS    = 0x0005;
  public static final int MVE_OC_UNKNOWN_06       = 0x0006;
  public static final int MVE_OC_PLAY_VIDEO       = 0x0007;
  public static final int MVE_OC_AUDIO_DATA       = 0x0008;
  public static final int MVE_OC_AUDIO_SILENCE    = 0x0009;
  public static final int MVE_OC_VIDEO_MODE       = 0x000a;
  public static final int MVE_OC_CREATE_GRADIENT  = 0x000b;
  public static final int MVE_OC_PALETTE          = 0x000c;
  public static final int MVE_OC_PALETTE_PACKED   = 0x000d;
  public static final int MVE_OC_UNKNOWN_0E       = 0x000e;
  public static final int MVE_OC_CODE_MAP         = 0x000f;
  public static final int MVE_OC_UNKNOWN_10       = 0x0010;
  public static final int MVE_OC_VIDEO_DATA       = 0x0011;
  public static final int MVE_OC_UNKNOWN_12       = 0x0012;
  public static final int MVE_OC_UNKNOWN_13       = 0x0013;
  public static final int MVE_OC_UNKNOWN_14       = 0x0014;
  public static final int MVE_OC_UNKNOWN_15       = 0x0015;

  // audio specific flags
  public static final int MVE_AUDIO_STEREO        = 0x0001;
  public static final int MVE_AUDIO_16BIT         = 0x0002;
  public static final int MVE_AUDIO_COMPRESSED    = 0x0004;

  // video specific flags
  public static final int MVE_VIDEO_DELTA         = 0x0001;

  private final MveInfo info; // contains all required information to decode the MVE resource

  private boolean initialized;

  /**
   * Creates an empty (closed) MveDecoder object. No data processing is possible until the {@code open()} method has
   * been called successfully.
   */
  public MveDecoder() {
    initialized = false;
    info = new MveInfo();
  }

  /**
   * Creates and initializes a new MveDecoder object of the specified resource entry.
   *
   * @param entry The MVE resource.
   * @throws Exception
   */
  public MveDecoder(ResourceEntry entry) throws Exception {
    if (entry == null) {
      throw new NullPointerException();
    }

    initialized = false;
    info = new MveInfo();
    open(entry.getResourceDataAsStream());
  }

  /**
   * Creates and initializes a new MveDecoder object of the specified MVE data block.
   *
   * @param mveData The MVE input data block.
   * @param mveOfs  Start offset in the MVE data block.
   * @param mveLen  Size of the MVE data block in bytes.
   * @throws Exception
   */
  public MveDecoder(byte[] mveData, int mveOfs, int mveLen) throws Exception {
    if (mveData == null) {
      throw new NullPointerException();
    }
    if (mveOfs < 0 || mveLen < 0 || mveOfs + mveLen > mveData.length) {
      throw new Exception("Invalid input data offset or size specified");
    }

    initialized = false;
    info = new MveInfo();
    open(new ByteArrayInputStream(mveData, mveOfs, mveLen));
  }

  /**
   * Creates and initializes a new MveDecoder object of the specified MVE data stream.
   *
   * @param mveIn The input stream containing MVE data.
   * @throws Exception
   */
  public MveDecoder(InputStream mveIn) throws Exception {
    initialized = false;
    info = new MveInfo();
    open(mveIn);
  }

  /**
   * Opens and initializes a new MVE resource.
   *
   * @param entry The MVE resource.
   * @throws Exception
   */
  public void open(ResourceEntry entry) throws Exception {
    if (entry == null) {
      throw new NullPointerException();
    }

    open(entry.getResourceDataAsStream());
  }

  /**
   * Opens and initializes a new MVE input data block.
   *
   * @param mveData The MVE input data block.
   * @param mveOfs  Start offset in the MVE data block.
   * @param mveLen  Size of the MVE data block in bytes.
   * @throws Exception
   */
  public void open(byte[] mveData, int mveOfs, int mveLen) throws Exception {
    if (mveData == null) {
      throw new NullPointerException();
    }
    if (mveOfs < 0 || mveLen < 0 || mveOfs + mveLen > mveData.length) {
      throw new Exception("Invalid input data offset or size specified");
    }

    open(new ByteArrayInputStream(mveData, mveOfs, mveLen));
  }

  /**
   * Opens and initializes a new MVE data stream.
   *
   * @param mveIn The input stream containing MVE data.
   * @throws Exception
   */
  public void open(InputStream mveIn) throws Exception {
    close();

    if (mveIn == null) {
      throw new NullPointerException();
    }

    info.mveInput = mveIn;
    info.audioDecoder = MveAudioDecoder.createDecoder(info);
    info.videoDecoder = MveVideoDecoder.createDecoder(info);

    // 1. analyzing MVE header
    byte[] buf = new byte[MVE_SIGNATURE.length()];
    if (info.mveInput.read(buf) < buf.length) {
      throw new Exception("Unexpected end of file");
    }
    if (!Arrays.equals(MVE_SIGNATURE.getBytes(), buf)) {
      if (BIK_SIGNATURE.equals(new String(buf, 0, BIK_SIGNATURE.length()))) {
        throw new UnsupportedFormatException("Bink video format not supported.");
      } else {
        throw new Exception("Invalid MVE signature found");
      }
    }

    // 2. initializing MveChunk structure
    if (!info.chunk.init(info.mveInput)) {
      throw new Exception("Error initializing MVE data stream");
    }

    // 3. processing initialization chunks
    while (info.chunk.getNextType() != MVE_CHUNK_NONE) {
      // looking for audio and video initialization chunks before processing any output chunk
      if (info.chunk.getNextType() == MVE_CHUNK_INIT_AUDIO || info.chunk.getNextType() == MVE_CHUNK_INIT_VIDEO) {
        if (info.chunk.loadChunk()) {
          if (!manageChunk(info.chunk)) {
            throw new Exception("Error processing chunk: " + String.format("0x%02x", info.chunk.getType()));
          }
        } else {
          throw new Exception("Unable to load next chunk");
        }
      } else {
        break;
      }
    }

    initialized = (info.chunk.getNextType() != MVE_CHUNK_NONE);
  }

  /**
   * Closes a MVE data stream. Connected video and audio output objects are not affected.
   */
  public void close() {
    initialized = false;
    info.close();
  }

  /**
   * Returns whether a MVE resource is already attached to the decoder.
   *
   * @return {@code true} if a MVE resource is already attached to the decoder.
   */
  public boolean isOpen() {
    return initialized;
  }

  /**
   * Connects a video output object with the decoder. (Note: The decoder calls {@code VideoBuffer.flip()} automatically
   * after each successfully processed frame.
   *
   * @param videoOut The VideoBuffer object to render the decoded frames into.
   */
  public void setVideoOutput(VideoBuffer videoOut) {
    info.videoOutput = videoOut;
  }

  /**
   * Returns the currently connected video output object.
   *
   * @return The currently connected video output object, or {@code null} if no video buffer is available.
   */
  public VideoBuffer getVideoOutput() {
    return info.videoOutput;
  }

  /**
   * Connects an audio output queue with the default audio stream (stream 0). The decoder will add new decoded audio
   * blocks to the queue whenever a new frame has been processed. The data can be accessed by the AudioQueue methods.
   *
   * @param audioOut The AudioQueue object.
   */
  public void setDefaultAudioOutput(AudioQueue audioOut) {
    info.audioOutput.set(0, audioOut);
    if (audioOut != null) {
      info.audioOutputMask |= 1;
    } else {
      info.audioOutputMask &= ~1;
    }
  }

  /**
   * Returns the currently connected audio output queue.
   *
   * @return The currently connected AudioQueue object, or {@code null} if no audio output has been defined yet.
   */
  public AudioQueue getDefaultAudioOutput() {
    return info.audioOutput.get(0);
  }

  /**
   * Connects an audio output queue with the specified audio stream. Valid streams range from 0 to 15. The decoder will
   * add new decoded audio blocks to the queue whenever a new frame has been processed. The data can be accessed by the
   * AudioQueue methods.
   *
   * @param index    The audio stream to connect (range: 0..15).
   * @param audioOut The AudioQueue object.
   * @throws IndexOutOfBoundsException If index is out of range.
   */
  public void setAudioOutput(int index, AudioQueue audioOut) throws IndexOutOfBoundsException {
    if (index < 0 || index > 15) {
      throw new IndexOutOfBoundsException("Index out of bounds: " + index);
    }

    info.audioOutput.set(index, audioOut);
    if (audioOut != null) {
      info.audioOutputMask |= (1 << index);
    } else {
      info.audioOutputMask &= ~(1 << index);
    }
  }

  /**
   * Returns the audio output queue of the specified stream. Valid streams range from 0 to 15.
   *
   * @param index The audio stream to query (range: 0..15).
   * @return The connected AudioQueue object of the specified stream, or {@code null} if no audio output had been
   *         defined yet for the specified stream.
   * @throws IndexOutOfBoundsException If index is out of range.
   */
  public AudioQueue getAudioOutput(int index) throws IndexOutOfBoundsException {
    if (index < 0 || index > 15) {
      throw new IndexOutOfBoundsException("Index out of bounds: " + index);
    }

    return info.audioOutput.get(index);
  }

  /**
   * Returns the audio format detected in the MVE data stream.
   *
   * @return The audio format detected in the MVE data stream.
   */
  public AudioFormat getAudioFormat() {
    return info.audioFormat;
  }

  /**
   * Returns whether the MVE contains audio streams.
   *
   * @return Whether the MVE contains audio streams.
   */
  public boolean isAudioAvailable() {
    if (isOpen()) {
      return info.audioAvailable;
    } else {
      return false;
    }
  }

  /**
   * Returns the width of the MVE video stream in pixels.
   *
   * @return The width of the MVE video stream in pixels.
   */
  public int getVideoWidth() {
    if (isOpen()) {
      return info.width;
    } else {
      return 0;
    }
  }

  /**
   * Returns the height of the MVE video stream in pixels.
   *
   * @return The height of the MVE video stream in pixels.
   */
  public int getVideoHeight() {
    if (isOpen()) {
      return info.height;
    } else {
      return 0;
    }
  }

  /**
   * Returns the delay per frame of the video.
   *
   * @return The delay per frame in microseconds.
   */
  public int getFrameDelay() {
    if (isOpen()) {
      return info.frameDelay;
    } else {
      return 0;
    }
  }

  /**
   * If true, the frame delay does not change after each processed frame.
   *
   * @return {@code true} if the frame delay is stable for the remaining frames, {@code false} otherwise.
   */
  public boolean isFrameDelayStable() {
    if (isOpen()) {
      return info.isFrameDelayStable;
    } else {
      return false;
    }
  }

  /**
   * Returns whether video has been initialized in the last processed frame.
   *
   * @return {@code true} if video has been (re-)initialized in the last frame, {@code false} otherwise.
   */
  public boolean videoInitialized() {
    return info.videoInitialized;
  }

  /**
   * Returns whether audio has been initialized in the last processed frame.
   *
   * @return {@code true} if audio has been (re-)initialized in the last frame, {@code false} otherwise.
   */
  public boolean audioInitialized() {
    return info.audioInitialized;
  }

  /**
   * Indicates whether the current frame contains video data.
   *
   * @return {@code true} if the frame contains video data, {@code false} otherwise.
   */
  public boolean frameHasVideo() {
    return (info.chunk.getType() == MVE_CHUNK_VIDEO);
  }

  /**
   * Indicates whether the current frame contains audio data.
   *
   * @return {@code true} if the frame contains audio data, {@code false} otherwise.
   */
  public boolean frameHasAudio() {
    return (info.chunk.getType() == MVE_CHUNK_VIDEO || info.chunk.getType() == MVE_CHUNK_AUDIO_ONLY);
  }

  /**
   * Determines whether the MVE data stream contains more frames.
   *
   * @return {@code true} if there is at least one more frame is available, {@code false} otherwise.
   */
  public boolean hasNextFrame() {
    if (isOpen()) {
      switch (info.chunk.getNextType()) {
        case MVE_CHUNK_SHUTDOWN:
        case MVE_CHUNK_END:
        case MVE_CHUNK_NONE:
          return false;
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Signals the decoder to process the MVE data stream until a new frame has been rendered. The result can be accessed
   * via the connected VideoBuffer (video) and AudioQueue(s) (audio).
   *
   * @return {@code true} if a frame has been decoded successfully, {@code false} otherwise.
   * @throws Exception
   */
  public boolean processNextFrame() throws Exception {
    while (hasNextFrame()) {
      if (!info.chunk.loadChunk()) {
        throw new Exception("Error loading next chunk");
      }

      switch (info.chunk.getType()) {
        case MVE_CHUNK_AUDIO_ONLY:
        case MVE_CHUNK_VIDEO:
          if (!manageChunk(info.chunk)) {
            throw new Exception("Error processing chunk");
          }
          return true;
        default:
          if (!manageChunk(info.chunk)) {
            throw new Exception("Error processing chunk");
          }
      }
    }
    return false;
  }

  private boolean manageChunk(MveChunk chunk) throws Exception {
    if (chunk != null) {
      switch (chunk.getType()) {
        case MVE_CHUNK_INIT_AUDIO:
        case MVE_CHUNK_INIT_VIDEO:
        case MVE_CHUNK_AUDIO_ONLY:
        case MVE_CHUNK_VIDEO:
        case MVE_CHUNK_SHUTDOWN:
        case MVE_CHUNK_END: {
          while (chunk.hasNextSegment()) {
            if (!manageSegment(chunk, chunk.nextSegment())) {
              return false;
            }
          }
          return true;
        }
        default:
          throw new Exception("Invalid chunk type: " + String.format("0x%02x", chunk.getType()));
      }
    }
    return false;
  }

  private boolean manageSegment(MveChunk chunk, MveSegment segment) {
    if (segment != null) {
      try {
        switch (segment.getOpcode()) {
          case MVE_OC_END_OF_STREAM: // clean up stream specific data data
            info.audioDecoder.processAudio(segment);
            info.videoDecoder.processVideo(segment);
            // cleanUp();
            return true;
          case MVE_OC_END_OF_CHUNK: // marks end of the current chunk
            switch (chunk.getType()) {
              case MVE_CHUNK_AUDIO_ONLY:
                return info.audioDecoder.processAudio(segment);
              case MVE_CHUNK_VIDEO:
                return info.videoDecoder.processVideo(segment);
              default:
                return true;
            }
          case MVE_OC_AUDIO_BUFFERS: // audio format initialization
          case MVE_OC_PLAY_AUDIO: // not needed by the decoder
          case MVE_OC_AUDIO_DATA: // decode a frame worth of audio data
          case MVE_OC_AUDIO_SILENCE: // generate a frame worth of silence
            return info.audioDecoder.processAudio(segment);
          case MVE_OC_CREATE_TIMER: // can be found in the first video chunk
          case MVE_OC_VIDEO_BUFFERS: // video format initialization
          case MVE_OC_PLAY_VIDEO: // outputs back buffer
          case MVE_OC_VIDEO_MODE: // not needed by the decoder
          case MVE_OC_CREATE_GRADIENT: // not used in IE games
          case MVE_OC_PALETTE: // initialize palette (indexed color mode only)
          case MVE_OC_PALETTE_PACKED: // modify palette (indexed color mode only)
          case MVE_OC_CODE_MAP: // provides code map for next video frame
          case MVE_OC_VIDEO_DATA: // decodes current code map into a video frame
            return info.videoDecoder.processVideo(segment);
          default: // processing unknown opcodes
            return true;
        }
      } catch (Exception e) {
        Logger.error(e);
      }
    }
    return false;
  }

  // ----------------------------- INNER CLASSES -----------------------------

  /**
   * Storage class for MVE related data.
   */
  public static class MveInfo {
    // max. number of supported audio streams in MVEs
    public static final int AUDIOSTREAMS_MAX = 16;

    // The default audio format to use if no audio is defined in the MVE data stream
    private static final AudioFormat DEFAULT_AUDIO_FORMAT =
        new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 2, 4, 22050, false);

    public final MveChunk chunk; // stores preprocessed data of the current chunk
    public final List<AudioQueue> audioOutput; // list of externally connected audio queues

    public InputStream mveInput;          // the MVE data stream
    public VideoBuffer videoOutput;       // externally connected video buffer
    public int width;                     // video width and height
    public int height;                    // video width and height
    public boolean isPalette;             // true if data uses palettes
    public int audioOutputMask;           // bitmask tells which audio output has been set
    public AudioFormat audioFormat;       // the audio format used by the current MVE
    public boolean audioCompressed;       // indicates whether audio data is compressed
    public boolean audioAvailable;        // is audio available in MVE
    public boolean isFrameDelayStable;    // indicates whether a stable frame delay has been set (via opcode 0x02)
    public int frameDelay;                // delay per frame in microseconds
    public int currentFrame;              // index of the last processed video frame
    public boolean videoInitialized;      // true if video has been (re-)initialized in the last frame
    public boolean audioInitialized;      // true if audio has been (re-)initialized in the last frame

    private MveAudioDecoder audioDecoder; // audio decoder, used internally
    private MveVideoDecoder videoDecoder; // video decoder, used internally

    // initialize MVE related data
    private MveInfo() {
      chunk = new MveChunk();

      mveInput = null;
      videoOutput = null;
      width = height = 0;
      isPalette = true;

      audioOutput = new Vector<>(AUDIOSTREAMS_MAX);
      for (int i = 0; i < AUDIOSTREAMS_MAX; i++) {
        audioOutput.add(null);
      }
      audioOutputMask = 0x0000;
      audioFormat = DEFAULT_AUDIO_FORMAT;
      audioCompressed = false;
      audioAvailable = false;
      isFrameDelayStable = false;
      frameDelay = 0;
      currentFrame = -1;

      videoDecoder = null;
      audioDecoder = null;
    }

    // called whenever a MVE data stream is closed
    private void close() {
      if (mveInput != null) {
        try {
          mveInput.close();
        } catch (IOException e) {
          Logger.trace(e);
        }
        mveInput = null;
      }
      if (audioDecoder != null) {
        audioDecoder.close();
        audioDecoder = null;
      }
      if (videoDecoder != null) {
        videoDecoder.close();
        videoDecoder = null;
      }
      audioFormat = DEFAULT_AUDIO_FORMAT;
      audioAvailable = false;
      width = height = 0;
      isPalette = false;
      isFrameDelayStable = false;
      frameDelay = 0;
      currentFrame = -1;
    }
  }

  /**
   * Stores preprocessed data of a single MVE chunk.
   */
  public static class MveChunk {

    private boolean initialized;              // indicates whether the object is in a valid state
    private InputStream in;                   // MVE data input stream
    private final List<MveSegment> segments;  // list of chunk segments
    private int segmentIndex;                 // current segment to query
    private int curChunkSize;                 // current chunk size
    private int curChunkType;                 // current chunk type
    private int nextChunkSize;                // information about the next chunk to load
    private int nextChunkType;                // information about the next chunk to load
                                              // (MVE_CHUNK_NONE when no more chunks available)

    /**
     * Read chunk data from input stream. Input stream must already be positioned at the start of a new chunk.
     */
    private MveChunk() {
      initialized = false;
      segments = new ArrayList<>(10);
      segmentIndex = 0;
    }

    /**
     * Must be called once to initialize a new MVE data stream. <b>Note:</b> The MVE data stream must already be
     * positioned right before a MVE chunk.
     *
     * @param in The MVE data stream
     * @return {@code true} if initialization was successful, {@code false} otherwise.
     */
    public boolean init(InputStream in) {
      if (in == null) {
        throw new NullPointerException();
      }
      this.in = in;
      segments.clear();
      segmentIndex = 0;
      curChunkSize = curChunkType = MVE_CHUNK_NONE;
      initialized = true;
      return peekNextChunk();
    }

    /**
     * Loads and preprocesses the next available chunk.
     *
     * @return {@code true} if another chunk is available, {@code false} otherwise.
     */
    public boolean loadChunk() {
      if (initialized) {
        return initSegments();
      } else {
        return false;
      }
    }

    /**
     * Returns the size of the next chunk to load.
     *
     * @return Size of the next chunk or MVE_CHUNK_NONE if no more chunks available.
     */
    public int getNextSize() {
      if (initialized) {
        return nextChunkSize;
      } else {
        return MVE_CHUNK_NONE;
      }
    }

    /**
     * Returns the type of the next chunk to load.
     *
     * @return Chunk type or MVE_CHUNK_NONE if no more chunks available.
     */
    public int getNextType() {
      if (initialized) {
        return nextChunkType;
      } else {
        return MVE_CHUNK_NONE;
      }
    }

    /**
     * Returns chunk type.
     */
    public int getType() {
      if (initialized) {
        return curChunkType;
      } else {
        return MVE_CHUNK_NONE;
      }
    }

    /**
     * Returns chunk size without chunk header.
     */
    public int getSize() {
      if (initialized) {
        return curChunkSize;
      } else {
        return MVE_CHUNK_NONE;
      }
    }

    /**
     * Returns whether more segments are available.
     */
    public boolean hasNextSegment() {
      return (initialized && segmentIndex < segments.size());
    }

    /**
     * Returns the next segment in line.
     */
    public MveSegment nextSegment() {
      if (hasNextSegment()) {
        return segments.get(segmentIndex++);
      } else {
        return null;
      }
    }

    /**
     * Returns number of segments in the current chunk.
     *
     * @return Number of segments in the current chunk.
     */
    public int getSegmentCount() {
      if (initialized) {
        return segments.size();
      } else {
        return 0;
      }
    }

    @Override
    public String toString() {
      return String.format("Chunk 0x%02x (%d bytes): segment #%d (opcode=%02x, size=%d)", curChunkType, curChunkSize,
          segmentIndex, segments.get(segmentIndex).opcode, segments.get(segmentIndex).size);
    }

    private boolean initSegments() {
      if (initialized && in != null) {
        segments.clear();
        segmentIndex = 0;
        int curSize = 0;
        if (curChunkType != MVE_CHUNK_END) {
          curChunkSize = nextChunkSize;
          curChunkType = nextChunkType;
          while (curSize < curChunkSize) {
            try {
              int segmentSize = StreamUtils.readUnsignedShort(in);
              short segmentOpcode = StreamUtils.readUnsignedByte(in);
              short segmentVersion = StreamUtils.readUnsignedByte(in);
              curSize += 4;
              MveSegment segment = new MveSegment(in, segmentSize, segmentOpcode, segmentVersion);
              segments.add(segment);
              curSize += segmentSize;
            } catch (IOException e) {
              Logger.error(e);
              return false;
            }
          }
          return peekNextChunk();
        } else {
          curChunkSize = curChunkType = MVE_CHUNK_NONE;
          initialized = false;
        }
      }
      return false;
    }

    private boolean peekNextChunk() {
      if (initialized && in != null) {
        if (curChunkType == MVE_CHUNK_END) {
          nextChunkSize = nextChunkType = MVE_CHUNK_NONE;
        } else {
          try {
            nextChunkSize = StreamUtils.readUnsignedShort(in);
            nextChunkType = StreamUtils.readUnsignedShort(in);
          } catch (IOException e) {
            Logger.error(e);
            return false;
          }
        }
        return true;
      } else {
        nextChunkSize = nextChunkType = MVE_CHUNK_NONE;
        return false;
      }
    }
  }

  /**
   * Stores preprocessed data of a single MVE segment.
   */
  public static class MveSegment {
    private final int size;
    private final int opcode;
    private final int version;
    private final byte[] data;
    private int dataOfs;
    private int bitsAvail;
    private int bitsNext;
    private int dataOfs2;
    private int bitsAvail2;
    private int bitsNext2;

    /**
     * Read segment data from input stream, using size, opcode and version as parameters.
     *
     * @param in      The MVE data stream
     * @param size    The size of the segment data without header size.
     * @param opcode  The segment opcode.
     * @param version The opcode version.
     */
    private MveSegment(InputStream in, int size, int opcode, int version) {
      this.size = size;
      this.opcode = opcode;
      this.version = version;
      data = new byte[size];
      dataOfs = bitsAvail = bitsNext = 0;
      dataOfs2 = bitsAvail2 = bitsNext2 = 0;

      if (in != null) {
        int numRead = 0;
        while (numRead < size) {
          try {
            int n = in.read(data);
            if (n <= 0) {
              break;
            }
            numRead += n;
          } catch (IOException e) {
            Logger.trace(e);
          }
        }
      }
    }

    /**
     * Returns segment opcode.
     *
     * @return The segment opcode.
     */
    public int getOpcode() {
      return opcode;
    }

    /**
     * Returns size of segment data (without initial size/opcode/version fields).
     *
     * @return Segment data size without header size.
     */
    public int getSize() {
      return size;
    }

    /**
     * Returns opcode version.
     *
     * @return The opcode version.
     */
    public int getVersion() {
      return version;
    }

    /**
     * Returns whether the buffer contains more data to fetch.
     *
     * @return {@code true} if more data is available, {@code false} otherwise.
     */
    public boolean available() {
      return (dataOfs < data.length || bitsAvail > 0);
    }

    /**
     * Returns whether the buffer contains more data to fetch from extra offset. Note: This method is required for a
     * number of video codes in direct color mode.
     *
     * @return {@code true} if more data is available, {@code false} otherwise.
     */
    public boolean availableExtra() {
      return (dataOfs2 < data.length || bitsAvail2 > 0);
    }

    /**
     * Returns the current offset in the internal buffer.
     *
     * @return The current offset in bytes.
     */
    public int getOffset() {
      return dataOfs;
    }

    /**
     * Returns the current extra offset in the internal buffer. Note: This method is required for a number of video
     * codes in direct color mode.
     *
     * @return The current offset in bytes.
     */
    public int getOffsetExtra() {
      return dataOfs2;
    }

    /**
     * Sets a new offset in the internal buffer. Clears all data already buffered.
     *
     * @param newOfs The new offset in the internal buffer.
     */
    public void setOffset(int newOfs) {
      if (newOfs >= 0 && newOfs < data.length) {
        dataOfs = newOfs;
        bitsNext = bitsAvail = 0;
      }
    }

    /**
     * Sets a new extra offset in the internal buffer. Clears all data already buffered. Note: This method is required
     * for a number of video codes in direct color mode.
     *
     * @param newOfs The new offset in the internal buffer.
     */
    public void setOffsetExtra(int newOfs) {
      if (newOfs >= 0 && newOfs < data.length) {
        dataOfs2 = newOfs;
        bitsNext2 = bitsAvail2 = 0;
      }
    }

    /**
     * Requests and returns specified number of bits from internal buffer.
     *
     * @param bits Number of bits to fetch from the segment data.
     * @return Number of bits as an unsigned integer value.
     */
    public int getBits(int bits) {
      int res = 0;
      if (bits > 0) {
        // prepare bits for output
        while (bits > bitsAvail) {
          if (dataOfs >= data.length) {
            break;
          }
          bitsNext |= (data[dataOfs++] & 0xff) << bitsAvail;
          bitsAvail += 8;
        }

        // output bits
        res = bitsNext;
        if (bits < 32) {
          res &= (1 << bits) - 1;
          bitsNext >>>= bits;
        } else {
          bitsNext = 0;
        }
        bitsAvail -= bits;
      }
      return res;
    }

    /**
     * Requests and returns specified number of bits from internal buffer's extra offset. Note: This method is required
     * for a number of video codes in direct color mode.
     *
     * @param bits Number of bits to fetch from the segment data.
     * @return Number of bits as an unsigned integer value.
     */
    public int getBitsExtra(int bits) {
      int res = 0;
      if (bits > 0) {
        // prepare bits for output
        while (bits > bitsAvail2) {
          if (dataOfs2 >= data.length) {
            break;
          }
          bitsNext2 |= (data[dataOfs2++] & 0xff) << bitsAvail2;
          bitsAvail2 += 8;
        }

        // output bits
        res = bitsNext2;
        if (bits < 32) {
          res &= (1 << bits) - 1;
          bitsNext2 >>>= bits;
        } else {
          bitsNext2 = 0;
        }
        bitsAvail2 -= bits;
      }
      return res;
    }

    /**
     * Provides direct access to underlying data.
     *
     * @return Raw byte array of the segment data.
     */
    public byte[] getData() {
      return data;
    }

    @Override
    public String toString() {
      return String.format("Segment 0x%02x (%d bytes): offset=#%d, ofsExtra=%d", opcode, size, dataOfs, dataOfs2);
    }
  }
}
