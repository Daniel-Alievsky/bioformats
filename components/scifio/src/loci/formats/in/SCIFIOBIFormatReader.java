package loci.formats.in;

import java.awt.image.BufferedImage;
import java.io.IOException;

import loci.common.DataTools;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.SCIFIOFormatReader;
import loci.formats.gui.AWTImageTools;

/**
 * Abstract superclass for file format readers that use
 * java.awt.image.BufferedImage as the native data type.
 * Defers to ome.scifio.in.BIFormatReader
 *
 */
@Deprecated
public abstract class SCIFIOBIFormatReader extends SCIFIOFormatReader {

  // -- Constructors --

  /** Constructs a new BIFormatReader. */
  public SCIFIOBIFormatReader(String name, String suffix) {
    super(name, suffix);
  }

  /** Constructs a new BIFormatReader. */
  public SCIFIOBIFormatReader(String name, String[] suffixes) {
    super(name, suffixes);
  }

  // -- IFormatReader API methods --

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  @Deprecated
  @Override
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    try {
      return reader.openBytes(getSeries(), no, buf, x, y, w, h);
    }
    catch (ome.scifio.FormatException e) {
      throw new FormatException(e);
    }
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#getNativeDataType() */
  public Class<?> getNativeDataType() {
    return BufferedImage.class;
  }
}
