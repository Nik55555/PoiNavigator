package com.progmatic.snowball.utils;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.nio.ByteOrder;
import java.nio.ByteBuffer;

public class TIFFStream {
    private static final short TIFF_TYPE_BYTE      = 1;
    private static final short TIFF_TYPE_SHORT     = 3;
    private static final short TIFF_TYPE_LONG      = 4;
    private static final short TIFF_TYPE_RATIONAL  = 5;
    //private static final short TIFF_TYPE_DOUBLE    = 12;

    private static final int TIFF_TAG_IMAGEWIDTH       = 0x100; // SHORT
    private static final int TIFF_TAG_IMAGELENGTH      = 0x101; // SHORT
    private static final int TIFF_TAG_BITSPERSAMPLE    = 0x102; // SHORT = 16
    private static final int TIFF_TAG_COMPRESSION      = 0x103; // SHORT = 1
    private static final int TIFF_TAG_PHOTOMETRIC      = 0x106; // SHORT = 1
    private static final int TIFF_TAG_STRIPOFFSETS     = 0x111; // LONG[]
    private static final int TIFF_TAG_SAMPLESPERPIXEL  = 0x115; // SHORT = 1
    private static final int TIFF_TAG_ROWSPERSTRIP     = 0x116; // SHORT = 1
    private static final int TIFF_TAG_STRIPBYTECOUNTS  = 0x117; // LONG[]
    private static final int TIFF_TAG_XRESOLUTION      = 0x11A; // RATIONAL
    private static final int TIFF_TAG_YRESOLUTION      = 0x11B; // RATIONAL
    private static final int TIFF_TAG_RESOLUTIONUNIT   = 0x128; // SHORT = 2 (Inch)

    private static ByteBuffer getBuffer(InputStream in, int size) throws IOException
    {
      ByteBuffer buf = ByteBuffer.allocate(size);
      int res;
      int pos = 0;
      while( (res = in.read(buf.array(), pos, size - pos)) > 0 ) {
        pos += res;
      }
      return buf;
    }

    private static ByteBuffer seek(ByteBuffer buf, int offset, int size)
    {
      ByteBuffer res = buf.duplicate();
      res.order(buf.order());
      res.position(offset);
      res.limit(offset + size);
      return res;
    }

    public static void readTIFF(ElevationData hgt, InputStream in, int size) throws Exception
    {
      readTIFF(hgt, getBuffer(in, size));
    }

    public static void readTIFF(ElevationData hgt, ByteBuffer buf) throws Exception
    {
      int res;
      int rows = 0;
      int columns = 0;
      ByteBuffer stripOffsets = null;
      short stripOffsetType = 0;
      int stripOffsetCount = 0;
      ByteBuffer stripByteCounts = null;
      short stripByteCountType = 0;

      res = buf.remaining();
      if (res < 8) {
        throw new Exception("Failed to read TIFF header (" + res + " bytes read)");
      }
      byte signature1 = buf.get();
      byte signature2 = buf.get();
      if (signature1 == 0x49 && signature2 == 0x49) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
      } else
      if (signature1 == 0x4D && signature2 == 0x4D) {
        buf.order(ByteOrder.BIG_ENDIAN);
      } else {
        throw new Exception(String.format("Unexpected TIFF signature: 0x%02X 0x%02X", signature1, signature2));
      }
      short magic = buf.getShort();
      if (magic != 42) {
        throw new Exception(String.format("Unexpected TIFF magic: %d. The correct answer is 42", magic));
      }

      int IFDheaderOffset = buf.getInt();
      buf.position(IFDheaderOffset);

      res = buf.remaining();
      if (res < 2) {
        throw new Exception("Failed to read IFD header (" + res + " bytes read)");
      }
      short nEntries = buf.getShort();

      for(int entryNo = 0; entryNo < nEntries; ++entryNo) {
        res = buf.remaining();
        if (res < 12) {
          throw new Exception("Failed to read IFD entry #" + entryNo + " (" + res + " bytes read)");
        }
        int tag = buf.getShort() & 0xFFFF;
        short type = buf.getShort();
        int count = buf.getInt();
        int valuePos = buf.position();
        int value = buf.getInt();
        int offset = value;
        int valueSize = 0;

        switch(type) {
        case TIFF_TYPE_BYTE:
            value = buf.get(valuePos) & 0xFF;
            valueSize = 1;
            break;
        case TIFF_TYPE_SHORT:
            value = buf.getShort(valuePos) & 0xFFFF;
            valueSize = 2;
            break;
        case TIFF_TYPE_LONG:
            value = buf.getInt(valuePos);
            valueSize = 4;
            break;
        }
        switch(tag) {
        case TIFF_TAG_IMAGEWIDTH:
          columns = value;
          break;
        case TIFF_TAG_IMAGELENGTH:
          rows = value;
          break;
        case TIFF_TAG_BITSPERSAMPLE:
          if (value != 16) {
            throw new Exception("Unsipported BitsPerSample value " + value + ". Only 16bit images supported");
          }
          break;
        case TIFF_TAG_STRIPOFFSETS:
          stripOffsetType = type;
          stripOffsetCount = count;
          stripOffsets = seek(buf, count == 1 ? valuePos : offset, count * valueSize);
          break;
        case TIFF_TAG_SAMPLESPERPIXEL:
          if (value != 1) {
            throw new Exception("Unsipported SamplesPerPicel value " + value + ". Only 1-sample images supported");
          }
          break;
        case TIFF_TAG_STRIPBYTECOUNTS:
          if (stripOffsetCount != count) {
            throw new Exception("StripByteCounts count does not match StripOffsets count");
          }
          stripByteCountType = type;
          stripByteCounts = seek(buf, count == 1 ? valuePos : offset, count * valueSize);
          break;
        }
      }

      if (columns == 0) {
        throw new Exception("No ImageWidth (0x100) tag");
      }
      if (rows == 0) {
        throw new Exception("No ImageLength (0x101) tag");
      }
      if (stripOffsets == null) {
        throw new Exception("No StripOffsets (0x111) tag");
      }
      if (stripByteCounts == null) {
        throw new Exception("No StripByteCounts (0x117) tag");
      }

      hgt.setSize(rows, columns);
      int idata = 0;

      while(stripOffsets.remaining() > 0) {
        int sOffset = stripOffsetType == TIFF_TYPE_SHORT ? stripOffsets.getShort() : stripOffsets.getInt();
        int sSize = stripByteCountType == TIFF_TYPE_SHORT ? stripByteCounts.getShort() : stripByteCounts.getInt();
        ByteBuffer strip = seek(buf, sOffset, sSize);
        res = strip.remaining();
        if (res != sSize) {
          throw new Exception("Failed to read strip (" + res + " != " + sSize + ")");
        }
        if (strip.order() == ByteOrder.BIG_ENDIAN) {
          System.arraycopy(
            strip.array(), strip.position(),
            hgt.data, idata, sSize);
          idata += sSize;
        } else {
          while(strip.remaining() > 0) {
            hgt.set(idata, strip.getShort());
            idata += 2;
          }
        }
      }
      if (idata != columns * rows * 2) {
        throw new Exception("Read strips not matched image size");
      }
    }

    private static void writeIFDEntry(ByteBuffer out, int tag, short type, int count, int value)
    {
      out.putShort((short)tag);
      out.putShort(type);
      out.putInt(count);
      if (count > 1 || type != TIFF_TYPE_SHORT) {
        out.putInt(value);
      } else {
        out.putShort((short)value);
        out.putShort((short)0);
      }
    }

    public static void writeTIFF(ElevationData hgt, OutputStream out) throws IOException
    {
      int offset = 0;
      int size;

      int headerSize = size = 8;

      short IFDEntries = 11;
      int IFDSize = 12;
      int entriesOffset = offset = offset + size;
      int entriesSize = size = 2 /* number of entries */ + IFDEntries * IFDSize + 4 /* next directory offset */;

      int stripCount = 1; // or rows
      int stripOffsetOffset = offset = offset + size;
      int stripOffsetSize = size = stripCount > 1 ? 4 * stripCount : 0;

      int stripCountOffset = offset = offset + size;
      int stripCountSize = size = stripCount > 1 ? 4 * stripCount : 0;

      int resolutionOffset = offset = offset + size;
      int resolutionSize = size = 2 * 4;

      int dataOffset = offset = offset + size;
      int dataSize = size = hgt.rows * hgt.columns * 2;

      int stripSize = (dataSize / stripCount) + ((dataSize % stripCount) != 0 ? 1 : 0);
      int rowsPerStrip = (hgt.rows / stripCount) + ((hgt.rows % stripCount) != 0 ? 1 : 0);

      ByteBuffer buf = ByteBuffer.allocate( headerSize + entriesSize + stripOffsetSize + stripCountSize + resolutionSize );
      buf.order(ByteOrder.BIG_ENDIAN);

      // header
      buf.put((byte)0x4D);
      buf.put((byte)0x4D);
      buf.putShort((short)42);
      buf.putInt(entriesOffset);

      // number of entries
      buf.putShort(IFDEntries);

      // entries
      writeIFDEntry(buf, TIFF_TAG_IMAGEWIDTH, TIFF_TYPE_SHORT, 1, hgt.columns);
      writeIFDEntry(buf, TIFF_TAG_IMAGELENGTH, TIFF_TYPE_SHORT, 1, hgt.rows);
      writeIFDEntry(buf, TIFF_TAG_BITSPERSAMPLE, TIFF_TYPE_SHORT, 1, 16);
      writeIFDEntry(buf, TIFF_TAG_COMPRESSION, TIFF_TYPE_SHORT, 1, 1);
      writeIFDEntry(buf, TIFF_TAG_PHOTOMETRIC, TIFF_TYPE_SHORT, 1, 1);
      writeIFDEntry(buf, TIFF_TAG_STRIPOFFSETS, TIFF_TYPE_LONG, stripCount, stripCount > 1 ? stripOffsetOffset : dataOffset);
      writeIFDEntry(buf, TIFF_TAG_ROWSPERSTRIP, TIFF_TYPE_SHORT, 1, rowsPerStrip);
      writeIFDEntry(buf, TIFF_TAG_STRIPBYTECOUNTS, TIFF_TYPE_LONG, stripCount, stripCount > 1 ? stripCountOffset : dataSize);
      writeIFDEntry(buf, TIFF_TAG_XRESOLUTION, TIFF_TYPE_RATIONAL, 1, resolutionOffset);
      writeIFDEntry(buf, TIFF_TAG_YRESOLUTION, TIFF_TYPE_RATIONAL, 1, resolutionOffset);
      writeIFDEntry(buf, TIFF_TAG_RESOLUTIONUNIT, TIFF_TYPE_SHORT, 1, 2);

      // next directory
      buf.putInt(0);

      // strip offsets and counts
      if (stripCount > 1) {
        for(int i = 0; i < dataSize; i += stripSize) {
          buf.putInt( dataOffset + i );
        }
        for(int i = dataSize; i > 0; i += stripSize) {
          buf.putInt( Math.min(stripSize, i) );
        }
      }

      // resolution
      buf.putInt(100);
      buf.putInt(1);

      out.write(buf.array());
      out.write(hgt.data);
    }

}
