//
// TiffTools.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import loci.common.DataTools;
import loci.common.LogTools;
import loci.common.RandomAccessInputStream;
import loci.common.RandomAccessOutputStream;
import loci.formats.codec.BitBuffer;
import loci.formats.tiff.IFD;
import loci.formats.tiff.PhotoInterp;
import loci.formats.tiff.TiffCompression;
import loci.formats.tiff.TiffParser;
import loci.formats.tiff.TiffRational;

/**
 * A utility class for manipulating TIFF files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/TiffTools.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/TiffTools.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Eric Kjellman egkjellman at wisc.edu
 * @author Melissa Linkert linkert at wisc.edu
 * @author Chris Allan callan at blackcat.ca
 */
public final class TiffTools {

  // -- Constants --

  /** The number of bytes in each IFD entry. */
  public static final int BYTES_PER_ENTRY = 12;

  /** The number of bytes in each IFD entry of a BigTIFF file. */
  public static final int BIG_TIFF_BYTES_PER_ENTRY = 20;

  // TIFF header constants
  public static final int MAGIC_NUMBER = 42;
  public static final int BIG_TIFF_MAGIC_NUMBER = 43;
  public static final int LITTLE = 0x49;
  public static final int BIG = 0x4d;

  // -- Constructor --

  private TiffTools() { }

  // -- TiffTools API methods --

  /**
   * Tests the given data block to see if it represents
   * the first few bytes of a TIFF file.
   */
  public static boolean isValidHeader(byte[] block) {
    return checkHeader(block) != null;
  }

  /**
   * Checks the TIFF header.
   * @return true if little-endian,
   *         false if big-endian,
   *         or null if not a TIFF.
   */
  public static Boolean checkHeader(byte[] block) {
    try {
      RandomAccessInputStream in = new RandomAccessInputStream(block);
      TiffParser tiffParser = new TiffParser(in);
      Boolean result = tiffParser.checkHeader();
      in.close();
      return result;
    }
    catch (IOException e) {
      return null;
    }
  }

  // --------------------------- Reading TIFF files ---------------------------

  // -- IFD parsing methods --

  /** Convenience method for obtaining a file's first ImageDescription. */
  public static String getComment(String id) throws IOException {
    // read first IFD
    RandomAccessInputStream in = new RandomAccessInputStream(id);
    TiffParser tiffParser = new TiffParser(in);
    String comment = tiffParser.getComment();
    in.close();
    return comment;
  }

  // -- Image reading methods --

  /**
   * Extracts pixel information from the given byte array according to the
   * bits per sample, photometric interpretation, and the specified byte
   * ordering.
   * No error checking is performed.
   * This method is tailored specifically for planar (separated) images.
   */
  public static void planarUnpack(byte[] samples, int startIndex,
    byte[] bytes, IFD ifd) throws FormatException
  {
    BitBuffer bb = new BitBuffer(bytes);

    int numBytes = ifd.getBytesPerSample()[0];
    int realBytes = numBytes;
    if (numBytes == 3) numBytes++;

    int bitsPerSample = ifd.getBitsPerSample()[0];
    boolean littleEndian = ifd.isLittleEndian();
    int photoInterp = ifd.getPhotometricInterpretation();

    for (int j=0; j<bytes.length / realBytes; j++) {
      int value = bb.getBits(bitsPerSample);

      if (photoInterp == PhotoInterp.WHITE_IS_ZERO) {
        value = (int) (Math.pow(2, bitsPerSample) - 1 - value);
      }
      else if (photoInterp == PhotoInterp.CMYK) {
        value = Integer.MAX_VALUE - value;
      }

      if (numBytes*(startIndex + j) < samples.length) {
        DataTools.unpackBytes(value, samples, numBytes*(startIndex + j),
          numBytes, littleEndian);
      }
    }
  }

  /**
   * Extracts pixel information from the given byte array according to the
   * bits per sample, photometric interpretation and color map IFD directory
   * entry values, and the specified byte ordering.
   * No error checking is performed.
   */
  public static void unpackBytes(byte[] samples, int startIndex,
    byte[] bytes, IFD ifd) throws FormatException
  {
    if (ifd.getPlanarConfiguration() == 2) {
      planarUnpack(samples, startIndex, bytes, ifd);
      return;
    }

    int compression = ifd.getCompression();
    int photoInterp = ifd.getPhotometricInterpretation();
    if (compression == TiffCompression.JPEG) photoInterp = PhotoInterp.RGB;

    int[] bitsPerSample = ifd.getBitsPerSample();
    int nChannels = bitsPerSample.length;
    int nSamples = samples.length / nChannels;

    int totalBits = 0;
    for (int i=0; i<nChannels; i++) totalBits += bitsPerSample[i];
    int sampleCount = 8 * bytes.length / totalBits;
    if (photoInterp == PhotoInterp.Y_CB_CR) sampleCount *= 3;

    LogTools.debug("unpacking " + sampleCount + " samples (startIndex=" +
      startIndex + "; totalBits=" + totalBits +
      "; numBytes=" + bytes.length + ")");

    long imageWidth = ifd.getImageWidth();

    int bps0 = bitsPerSample[0];
    int numBytes = ifd.getBytesPerSample()[0];

    boolean noDiv8 = bps0 % 8 != 0;
    boolean bps8 = bps0 == 8;

    int row = startIndex / (int) imageWidth;
    int col = 0;

    int cw = 0, ch = 0;

    boolean littleEndian = ifd.isLittleEndian();

    int[] reference = ifd.getIFDIntArray(IFD.REFERENCE_BLACK_WHITE, false);
    int[] subsampling = ifd.getIFDIntArray(IFD.Y_CB_CR_SUB_SAMPLING, false);
    TiffRational[] coefficients = (TiffRational[])
      ifd.getIFDValue(IFD.Y_CB_CR_COEFFICIENTS);

    int count = 0;

    BitBuffer bb = new BitBuffer(bytes);

    for (int j=0; j<sampleCount; j++) {
      for (int i=0; i<nChannels; i++) {
        int index = numBytes * (j * nChannels + i);
        int ndx = startIndex + j;
        if (ndx >= nSamples) {
          break;
        }
        int outputIndex = i * nSamples + ndx * numBytes;

        if (noDiv8) {
          // bits per sample is not a multiple of 8

          short s = 0;
          if ((i == 0 && photoInterp == PhotoInterp.RGB_PALETTE) ||
            (photoInterp != PhotoInterp.CFA_ARRAY &&
            photoInterp != PhotoInterp.RGB_PALETTE))
          {
            s = (short) (bb.getBits(bps0) & 0xffff);
            if ((ndx % imageWidth) == imageWidth - 1 && bps0 < 8) {
              bb.skipBits((imageWidth * bps0 * sampleCount) % 8);
            }
          }

          if (photoInterp == PhotoInterp.WHITE_IS_ZERO ||
            photoInterp == PhotoInterp.CMYK)
          {
            // invert colors
            s = (short) (Math.pow(2, bitsPerSample[0]) - 1 - s);
          }

          if (outputIndex + numBytes <= samples.length) {
            DataTools.unpackBytes(s, samples, outputIndex, numBytes,
              littleEndian);
          }
        }
        else if (bps8) {
          // special case handles 8-bit data more quickly

          if (outputIndex >= samples.length) break;

          if (photoInterp != PhotoInterp.Y_CB_CR) {
            samples[outputIndex] = (byte) (bytes[index] & 0xff);
          }

          if (photoInterp == PhotoInterp.WHITE_IS_ZERO) { // invert color value
            samples[outputIndex] = (byte) (255 - samples[outputIndex]);
          }
          else if (photoInterp == PhotoInterp.CMYK) {
            samples[outputIndex] =
              (byte) (Integer.MAX_VALUE - samples[outputIndex]);
          }
          else if (photoInterp == PhotoInterp.Y_CB_CR) {
            if (i == bitsPerSample.length - 1) {
              float lumaRed = 0.299f;
              float lumaGreen = 0.587f;
              float lumaBlue = 0.114f;
              if (coefficients != null) {
                lumaRed = coefficients[0].floatValue();
                lumaGreen = coefficients[1].floatValue();
                lumaBlue = coefficients[2].floatValue();
              }

              int subX = subsampling == null ? 2 : subsampling[0];
              int subY = subsampling == null ? 2 : subsampling[1];

              int block = subX * subY;
              int lumaIndex = j + (2 * (j / block));
              int chromaIndex = (j / block) * (block + 2) + block;

              if (chromaIndex + 1 >= bytes.length) break;

              int tile = ndx / block;
              int pixel = ndx % block;
              int nTiles = (int) (imageWidth / subX);
              long r = subY * (tile / nTiles) + (pixel / subX);
              long c = subX * (tile % nTiles) + (pixel % subX);

              int idx = (int) (r * imageWidth + c);

              if (idx < nSamples) {
                int y = (bytes[lumaIndex] & 0xff) - reference[0];
                int cb = (bytes[chromaIndex] & 0xff) - reference[2];
                int cr = (bytes[chromaIndex + 1] & 0xff) - reference[4];

                int red = (int) (cr * (2 - 2 * lumaRed) + y);
                int blue = (int) (cb * (2 - 2 * lumaBlue) + y);
                int green = (int)
                  ((y - lumaBlue * blue - lumaRed * red) / lumaGreen);

                samples[idx] = (byte) red;
                samples[nSamples + idx] = (byte) green;
                samples[2*nSamples + idx] = (byte) blue;
              }
            }
          }
        }  // End if (bps8)
        else {
          int offset = numBytes + index < bytes.length ?
            index : bytes.length - numBytes;
          long v = DataTools.bytesToLong(bytes, offset, numBytes, littleEndian);

          if (photoInterp == PhotoInterp.WHITE_IS_ZERO) { // invert color value
            long max = (long) Math.pow(2, numBytes * 8) - 1;
            v = max - v;
          }
          else if (photoInterp == PhotoInterp.CMYK) {
            v = Integer.MAX_VALUE - v;
          }
          if (ndx*numBytes >= nSamples) break;
          DataTools.unpackBytes(v, samples, i*nSamples + ndx*numBytes,
            numBytes, littleEndian);
        } // end else
      }
    }
  }

  // --------------------------- Writing TIFF files ---------------------------

  // -- IFD writing methods --

  /**
   * Writes the given IFD value to the given output object.
   * @param ifdOut output object for writing IFD stream
   * @param extraBuf buffer to which "extra" IFD information should be written
   * @param extraOut data output wrapper for extraBuf (passed for efficiency)
   * @param offset global offset to use for IFD offset values
   * @param tag IFD tag to write
   * @param value IFD value to write
   */
  public static void writeIFDValue(DataOutput ifdOut,
    ByteArrayOutputStream extraBuf, DataOutputStream extraOut, long offset,
    int tag, Object value, boolean bigTiff, boolean littleEndian)
    throws FormatException, IOException
  {
    // convert singleton objects into arrays, for simplicity
    if (value instanceof Short) {
      value = new short[] {((Short) value).shortValue()};
    }
    else if (value instanceof Integer) {
      value = new int[] {((Integer) value).intValue()};
    }
    else if (value instanceof Long) {
      value = new long[] {((Long) value).longValue()};
    }
    else if (value instanceof TiffRational) {
      value = new TiffRational[] {(TiffRational) value};
    }
    else if (value instanceof Float) {
      value = new float[] {((Float) value).floatValue()};
    }
    else if (value instanceof Double) {
      value = new double[] {((Double) value).doubleValue()};
    }

    int dataLength = bigTiff ? 8 : 4;

    // write directory entry to output buffers
    DataTools.writeShort(ifdOut, tag, littleEndian); // tag
    if (value instanceof short[]) {
      short[] q = (short[]) value;
      DataTools.writeShort(ifdOut, IFD.BYTE, littleEndian);
      if (bigTiff) DataTools.writeLong(ifdOut, q.length, littleEndian);
      else DataTools.writeInt(ifdOut, q.length, littleEndian);
      if (q.length <= dataLength) {
        for (int i=0; i<q.length; i++) ifdOut.writeByte(q[i]);
        for (int i=q.length; i<dataLength; i++) ifdOut.writeByte(0);
      }
      else {
        if (bigTiff) {
          DataTools.writeLong(ifdOut, offset + extraBuf.size(), littleEndian);
        }
        else {
          DataTools.writeInt(ifdOut, (int) (offset + extraBuf.size()),
            littleEndian);
        }
        for (int i=0; i<q.length; i++) extraOut.writeByte(q[i]);
      }
    }
    else if (value instanceof String) { // ASCII
      char[] q = ((String) value).toCharArray();
      DataTools.writeShort(ifdOut, IFD.ASCII, littleEndian); // type
      if (bigTiff) DataTools.writeLong(ifdOut, q.length + 1, littleEndian);
      else DataTools.writeInt(ifdOut, q.length + 1, littleEndian);
      if (q.length < dataLength) {
        for (int i=0; i<q.length; i++) ifdOut.writeByte(q[i]); // value(s)
        for (int i=q.length; i<dataLength; i++) ifdOut.writeByte(0); // padding
      }
      else {
        if (bigTiff) {
          DataTools.writeLong(ifdOut, offset + extraBuf.size(), littleEndian);
        }
        else {
          // offset
          DataTools.writeInt(ifdOut, (int) (offset + extraBuf.size()),
            littleEndian);
        }
        for (int i=0; i<q.length; i++) extraOut.writeByte(q[i]); // values
        extraOut.writeByte(0); // concluding NULL byte
      }
    }
    else if (value instanceof int[]) { // SHORT
      int[] q = (int[]) value;
      DataTools.writeShort(ifdOut, IFD.SHORT, littleEndian); // type
      if (bigTiff) DataTools.writeLong(ifdOut, q.length, littleEndian);
      else DataTools.writeInt(ifdOut, q.length, littleEndian);
      if (q.length <= dataLength / 2) {
        for (int i=0; i<q.length; i++) {
          DataTools.writeShort(ifdOut, q[i], littleEndian); // value(s)
        }
        for (int i=q.length; i<dataLength / 2; i++) {
          DataTools.writeShort(ifdOut, 0, littleEndian); // padding
        }
      }
      else {
        if (bigTiff) {
          DataTools.writeLong(ifdOut, offset + extraBuf.size(), littleEndian);
        }
        else {
          // offset
          DataTools.writeInt(ifdOut, (int) (offset + extraBuf.size()),
            littleEndian);
        }
        for (int i=0; i<q.length; i++) {
          DataTools.writeShort(extraOut, q[i], littleEndian); // values
        }
      }
    }
    else if (value instanceof long[]) { // LONG
      long[] q = (long[]) value;

      if (bigTiff) {
        DataTools.writeShort(ifdOut, IFD.LONG8, littleEndian);
        DataTools.writeLong(ifdOut, q.length, littleEndian);

        if (q.length <= dataLength / 4) {
          for (int i=0; i<q.length; i++) {
            DataTools.writeLong(ifdOut, q[0], littleEndian);
          }
          for (int i=q.length; i<dataLength / 4; i++) {
            DataTools.writeLong(ifdOut, 0, littleEndian);
          }
        }
        else {
          DataTools.writeLong(ifdOut, offset + extraBuf.size(), littleEndian);
          for (int i=0; i<q.length; i++) {
            DataTools.writeLong(extraOut, q[i], littleEndian);
          }
        }
      }
      else {
        DataTools.writeShort(ifdOut, IFD.LONG, littleEndian);
        DataTools.writeInt(ifdOut, q.length, littleEndian);
        if (q.length <= dataLength / 4) {
          for (int i=0; i<q.length; i++) {
            DataTools.writeInt(ifdOut, (int) q[0], littleEndian);
          }
          for (int i=q.length; i<dataLength / 4; i++) {
            DataTools.writeInt(ifdOut, 0, littleEndian); // padding
          }
        }
        else {
          DataTools.writeInt(ifdOut, (int) (offset + extraBuf.size()),
            littleEndian);
          for (int i=0; i<q.length; i++) {
            DataTools.writeInt(extraOut, (int) q[i], littleEndian);
          }
        }
      }
    }
    else if (value instanceof TiffRational[]) { // RATIONAL
      TiffRational[] q = (TiffRational[]) value;
      DataTools.writeShort(ifdOut, IFD.RATIONAL, littleEndian); // type
      if (bigTiff) DataTools.writeLong(ifdOut, q.length, littleEndian);
      else DataTools.writeInt(ifdOut, q.length, littleEndian);
      if (bigTiff && q.length == 1) {
        DataTools.writeInt(ifdOut, (int) q[0].getNumerator(), littleEndian);
        DataTools.writeInt(ifdOut, (int) q[0].getDenominator(), littleEndian);
      }
      else {
        if (bigTiff) {
          DataTools.writeLong(ifdOut, offset + extraBuf.size(), littleEndian);
        }
        else {
          // offset
          DataTools.writeInt(ifdOut, (int) (offset + extraBuf.size()),
            littleEndian);
        }
        for (int i=0; i<q.length; i++) {
          DataTools.writeInt(extraOut, (int) q[i].getNumerator(), littleEndian);
          DataTools.writeInt(extraOut, (int) q[i].getDenominator(),
            littleEndian);
        }
      }
    }
    else if (value instanceof float[]) { // FLOAT
      float[] q = (float[]) value;
      DataTools.writeShort(ifdOut, IFD.FLOAT, littleEndian); // type
      if (bigTiff) DataTools.writeLong(ifdOut, q.length, littleEndian);
      else DataTools.writeInt(ifdOut, q.length, littleEndian);
      if (q.length <= dataLength / 4) {
        for (int i=0; i<q.length; i++) {
          DataTools.writeFloat(ifdOut, q[0], littleEndian); // value
        }
        for (int i=q.length; i<dataLength / 4; i++) {
          DataTools.writeInt(ifdOut, 0, littleEndian); // padding
        }
      }
      else {
        if (bigTiff) {
          DataTools.writeLong(ifdOut, offset + extraBuf.size(), littleEndian);
        }
        else {
          DataTools.writeInt(ifdOut, (int) (offset + extraBuf.size()),
            littleEndian);
        }
        for (int i=0; i<q.length; i++) {
          DataTools.writeFloat(extraOut, q[i], littleEndian); // values
        }
      }
    }
    else if (value instanceof double[]) { // DOUBLE
      double[] q = (double[]) value;
      DataTools.writeShort(ifdOut, IFD.DOUBLE, littleEndian); // type
      if (bigTiff) DataTools.writeLong(ifdOut, q.length, littleEndian);
      else DataTools.writeInt(ifdOut, q.length, littleEndian);
      if (bigTiff) {
        DataTools.writeLong(ifdOut, offset + extraBuf.size(), littleEndian);
      }
      else {
        DataTools.writeInt(ifdOut, (int) (offset + extraBuf.size()),
          littleEndian);
      }
      for (int i=0; i<q.length; i++) {
        DataTools.writeDouble(extraOut, q[i], littleEndian); // values
      }
    }
    else {
      throw new FormatException("Unknown IFD value type (" +
        value.getClass().getName() + "): " + value);
    }
  }

  /**
   * Surgically overwrites an existing IFD value with the given one. This
   * method requires that the IFD directory entry already exist. It
   * intelligently updates the count field of the entry to match the new
   * length. If the new length is longer than the old length, it appends the
   * new data to the end of the file and updates the offset field; if not, or
   * if the old data is already at the end of the file, it overwrites the old
   * data in place.
   */
  public static void overwriteIFDValue(String file,
    int ifd, int tag, Object value) throws FormatException, IOException
  {
    LogTools.debug("overwriteIFDValue (ifd=" + ifd + "; tag=" + tag +
      "; value=" + value + ")");
    byte[] header = new byte[4];

    RandomAccessInputStream raf = new RandomAccessInputStream(file);
    raf.seek(0);
    raf.readFully(header);
    if (!isValidHeader(header)) {
      throw new FormatException("Invalid TIFF header");
    }
    boolean little = header[0] == LITTLE && header[1] == LITTLE; // II
    boolean bigTiff = header[2] == 0x2b || header[3] == 0x2b;
    long offset = bigTiff ? 8 : 4; // offset to the IFD
    long num = 0; // number of directory entries

    int baseOffset = bigTiff ? 8 : 2;
    int bytesPerEntry = bigTiff ? BIG_TIFF_BYTES_PER_ENTRY : BYTES_PER_ENTRY;

    raf.seek(offset);

    // skip to the correct IFD
    for (int i=0; i<=ifd; i++) {
      offset = bigTiff ? DataTools.read8SignedBytes(raf, little) :
        DataTools.read4UnsignedBytes(raf, little);
      if (offset <= 0) {
        throw new FormatException("No such IFD (" + ifd + " of " + i + ")");
      }
      raf.seek(offset);
      num = bigTiff ? DataTools.read8SignedBytes(raf, little) :
        DataTools.read2UnsignedBytes(raf, little);
      if (i < ifd) raf.seek(offset + baseOffset + bytesPerEntry * num);
    }

    // search directory entries for proper tag
    for (int i=0; i<num; i++) {
      int oldTag = DataTools.read2UnsignedBytes(raf, little);
      int oldType = DataTools.read2UnsignedBytes(raf, little);
      int oldCount =
        bigTiff ? (int) (DataTools.read8SignedBytes(raf, little) & 0xffffffff) :
        DataTools.read4SignedBytes(raf, little);
      long oldOffset = bigTiff ? DataTools.read8SignedBytes(raf, little) :
        DataTools.read4SignedBytes(raf, little);
      if (oldTag == tag) {
        // write new value to buffers
        ByteArrayOutputStream ifdBuf = new ByteArrayOutputStream(bytesPerEntry);
        DataOutputStream ifdOut = new DataOutputStream(ifdBuf);
        ByteArrayOutputStream extraBuf = new ByteArrayOutputStream();
        DataOutputStream extraOut = new DataOutputStream(extraBuf);
        writeIFDValue(ifdOut, extraBuf, extraOut, oldOffset, tag, value,
          bigTiff, little);
        byte[] bytes = ifdBuf.toByteArray();
        byte[] extra = extraBuf.toByteArray();

        // extract new directory entry parameters
        int newTag = DataTools.bytesToInt(bytes, 0, 2, little);
        int newType = DataTools.bytesToInt(bytes, 2, 2, little);
        int newCount;
        long newOffset;
        if (bigTiff) {
          newCount =
            (int) (DataTools.bytesToLong(bytes, 4, little) & 0xffffffff);
          newOffset = DataTools.bytesToLong(bytes, 12, little);
        }
        else {
          newCount = DataTools.bytesToInt(bytes, 4, little);
          newOffset = DataTools.bytesToInt(bytes, 8, little);
        }
        boolean terminate = false;
        LogTools.debug("overwriteIFDValue:\n\told: (tag=" + oldTag +
          "; type=" + oldType + "; count=" + oldCount + "; offset=" +
          oldOffset + ");\n\tnew: (tag=" + newTag + "; type=" + newType +
          "; count=" + newCount + "; offset=" + newOffset + ")");

        // determine the best way to overwrite the old entry
        if (extra.length == 0) {
          // new entry is inline; if old entry wasn't, old data is orphaned
          // do not override new offset value since data is inline
          LogTools.debug("overwriteIFDValue: new entry is inline");
        }
        else if (oldOffset +
          oldCount * IFD.getIFDTypeLength(oldType) == raf.length())
        {
          // old entry was already at EOF; overwrite it
          newOffset = oldOffset;
          terminate = true;
          LogTools.debug("overwriteIFDValue: old entry is at EOF");
        }
        else if (newCount <= oldCount) {
          // new entry is as small or smaller than old entry; overwrite it
          newOffset = oldOffset;
          LogTools.debug("overwriteIFDValue: new entry is <= old entry");
        }
        else {
          // old entry was elsewhere; append to EOF, orphaning old entry
          newOffset = raf.length();
          LogTools.debug("overwriteIFDValue: old entry will be orphaned");
        }

        long filePointer = raf.getFilePointer();
        raf.close();

        RandomAccessOutputStream out = new RandomAccessOutputStream(file);

        // overwrite old entry
        out.seek(filePointer - (bigTiff ? 18 : 10)); // jump back
        DataTools.writeShort(out, newType, little);
        if (bigTiff) DataTools.writeLong(out, newCount, little);
        else DataTools.writeInt(out, newCount, little);
        if (bigTiff) DataTools.writeLong(out, newOffset, little);
        else DataTools.writeInt(out, (int) newOffset, little);
        if (extra.length > 0) {
          out.seek(newOffset);
          out.write(extra);
        }
        return;
      }
    }

    throw new FormatException("Tag not found (" + IFD.getIFDTagName(tag) + ")");
  }

  /** Convenience method for overwriting a file's first ImageDescription. */
  public static void overwriteComment(String id, Object value)
    throws FormatException, IOException
  {
    overwriteIFDValue(id, 0, IFD.IMAGE_DESCRIPTION, value);
  }

  // -- Image writing methods --

  /**
   * Write the header for a TIFF file to the given RandomAccessOutputStream.
   */
  public static void writeHeader(RandomAccessOutputStream out,
    boolean littleEndian, boolean bigTiff) throws IOException
  {
    // write endianness indicator
    if (littleEndian) {
      out.writeByte(LITTLE);
      out.writeByte(LITTLE);
    }
    else {
      out.writeByte(BIG);
      out.writeByte(BIG);
    }
    // write magic number
    if (bigTiff) {
      DataTools.writeShort(out, BIG_TIFF_MAGIC_NUMBER, littleEndian);
    }
    else DataTools.writeShort(out, MAGIC_NUMBER, littleEndian);

    // write the offset to the first IFD

    // for vanilla TIFFs, 8 is the offset to the first IFD
    // for BigTIFFs, 8 is the number of bytes in an offset
    DataTools.writeInt(out, 8, littleEndian);
    if (bigTiff) {
      // write the offset to the first IFD for BigTIFF files
      DataTools.writeLong(out, 16, littleEndian);
    }
  }

}
