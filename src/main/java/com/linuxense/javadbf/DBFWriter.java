/*

(C) Copyright 2015-2017 Alberto Fernández <infjaf@gmail.com>
(C) Copyright 2014 Jan Schlößin
(C) Copyright 2003-2004 Anil Kumar K <anil@linuxense.com>

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3.0 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library.  If not, see <http://www.gnu.org/licenses/>.

 */

package com.linuxense.javadbf;


import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

/*
 DBFWriter
 Class for defining a DBF structure and addin data to that structure and
 finally writing it to an OutputStream.

 */
/**
 * An object of this class can create a DBF file.
 *
 * Create an object, <br>
 * then define fields by creating DBFField objects and<br>
 * add them to the DBFWriter object<br>
 * add records using the addRecord() method and then<br>
 * call write() method.
 */
public class DBFWriter extends DBFBase implements java.io.Closeable {

	private DBFHeader header;
	private List<Object[]> v_records = new ArrayList<Object[]>();
	private int recordCount = 0;
	//Open and append records to an existing DBF
	private RandomAccessFile raf = null;
	private OutputStream outputStream = null;

	private boolean closed = false;

	/**
	 * Creates an empty DBFWriter.
	 * @deprecated use {@link #DBFWriter(OutputStream)}
	 */
	@Deprecated
	public DBFWriter() {
		this(DEFAULT_CHARSET);
	}
	/**
	 * Creates an empty DBFWriter.
	 * @param charset Charset used to encode field names and field contents
	 * @deprecated use {@link #DBFWriter(OutputStream, Charset)}
	 */
	@Deprecated
	public DBFWriter(Charset charset) {
		super();
		setCharset(charset);
		this.header = new DBFHeader();
		this.header.setUsedCharset(charset);
	}

	/**
	 * Creates a DBFWriter wich write data to the given OutputStream.
	 * Uses default charset iso-8859-1
	 * @param out stream to write the data to.
	 */

	public DBFWriter(OutputStream out) {
		this(out, DEFAULT_CHARSET);
	}
	/**
	 * Creates a DBFWriter wich write data to the given OutputStream.
	 * @param out stream to write the data to.
	 * @param charset Encoding to use in resulting dbf file
	 */
	public DBFWriter(OutputStream out, Charset charset) {
		this(out, charset, DBFFileFormat.COMPATIBLE);
	}
	/**
	 * Creates a DBFWriter wich write data to the given OutputStream.
	 * @param out stream to write the data to.
	 * @param charset Encoding to use in resulting dbf file
	 * @param format COMPATIBLE or ADVANCED
	 */
	public DBFWriter(OutputStream out, Charset charset, DBFFileFormat format) {
		super();
		setCharset(charset);
		this.header = new DBFHeader(format.getSignature());
		this.header.setUsedCharset(charset);
		this.outputStream = out;
	}

	/**
	 * Creates a DBFWriter which can append to records to an existing DBF file.
	 *
	 * @param dbfFile The file passed in shouls be a valid DBF file.
	 * @exception DBFException
	 *                if the passed in file does exist but not a valid DBF file,
	 *                or if an IO error occurs.
	 */
	public DBFWriter(File dbfFile) {
		this(dbfFile, null);
	}


	/**
	 * Creates a DBFWriter which can append to records to an existing DBF file.
	 *
	 * @param dbfFile The file passed in shouls be a valid DBF file.
	 * @param charset The charset used to encode field name and field contents
	 * @exception DBFException
	 *                if the passed in file does exist but not a valid DBF file,
	 *                or if an IO error occurs.
	 */
	public DBFWriter(File dbfFile, Charset charset) {
		this(dbfFile, charset, DBFFileFormat.COMPATIBLE);
	}
	
	/**
	 * Creates a DBFWriter which can append to records to an existing DBF file.
	 *
	 * @param dbfFile The file passed in shouls be a valid DBF file.
	 * @param charset The charset used to encode field name and field contents
	 * @param format COMPATIBLE or ADVANCED
	 * @exception DBFException
	 *                if the passed in file does exist but not a valid DBF file,
	 *                or if an IO error occurs.
	 */
	public DBFWriter(File dbfFile, Charset charset,DBFFileFormat format) {
		super();

		try {
			this.raf = new RandomAccessFile(dbfFile, "rw");
			this.header = new DBFHeader(format.getSignature());

			/*
			 * before proceeding check whether the passed in File object is an
			 * empty/non-existent file or not.
			 */
			if (dbfFile.length() == 0) {
				if (charset != null) {
					if (DBFCharsetHelper.getDBFCodeForCharset(charset) == 0 && !DBFStandardCharsets.UTF_8.equals(charset)) {
						throw new DBFException("Unssuported charset " + charset);
					}
					setCharset(charset);
					this.header.setUsedCharset(charset);
				}
				else {
					setCharset(DBFStandardCharsets.ISO_8859_1);
					this.header.setUsedCharset(DBFStandardCharsets.ISO_8859_1);
				}
			}
			else {
	
				this.header.read(this.raf, charset, false);
				setCharset(this.header.getUsedCharset());
	
				// position file pointer at the end of the raf
				// to ignore the END_OF_DATA byte at EoF
				// only if there are records,
				if (this.raf.length() > header.headerLength) {
					this.raf.seek(this.raf.length() - 1);
				}
				else {
					this.raf.seek(this.raf.length());
				}
			}
		} catch (FileNotFoundException e) {
			throw new DBFException("Specified file is not found. " + e.getMessage(), e);
		} catch (IOException e) {
			throw new DBFException(e.getMessage() + " while reading header", e);
		}

		this.recordCount = this.header.numberOfRecords;
	}



	/**
	 * Sets fields definition.
	 * To keep maximum compatibility a maximum of 255 columns should be used.
	 * https://docs.microsoft.com/en-us/previous-versions/visualstudio/foxpro/3kfd3hw9(v=vs.80)
	 * @param fields fields definition
	 */
	public void setFields(DBFField[] fields) {
		if (this.closed) {
			throw new IllegalStateException("You can not set fields to a closed DBFWriter");
		}
		try {
			if (this.raf != null && this.raf.length() > 0) {
				throw new DBFException("You can not change fields on an existing file");
			}
		}
		catch (IOException e) {
			throw new DBFException("Error accesing file:" + e.getMessage(), e);
		}
		if (this.header.fieldArray != null) {
			throw new DBFException("Fields has already been set");
		}
		if (fields == null || fields.length == 0) {
			throw new DBFException("Should have at least one field");
		}
		if (fields.length > 255) {
			throw new DBFException("Exceded column limit of 255 (" + fields.length + ")");
		}

		List<Integer> fieldsWithNull = new ArrayList<Integer>();
		for (int i = 0; i < fields.length; i++) {
			if (fields[i] == null) {
				fieldsWithNull.add(i);
			}
		}
		if (!fieldsWithNull.isEmpty()) {
			if (fieldsWithNull.size() == 1) {
				throw new DBFException("Field " + fieldsWithNull.get(0) + " is null");
			}
			else {
				throw new DBFException("Fields " + fieldsWithNull.toString()  + " are null");
			}
		}
		int maxFieldNameLength = 10;
		if (header.isDB7()) {
			maxFieldNameLength = 31;
		}
		for (DBFField field: fields) {
			if (!field.getType().isWriteSupported()) {
				throw new DBFException(
				"Field " + field.getName() + " is of type " + field.getType() + " that is not supported for writting");
			}
//			if (field.getType().getFileFormat() == DBFFileFormat.ADVANCED && !header.isDB7()) {
//				throw new DBFException(
//						"Field " + field.getName() + " is of type " + field.getType() + " that is only supported for writting in ADVANCED FILE FORMAT");
//			}
			int fieldNameLength = field.getName().getBytes(getCharset()).length;
			if (fieldNameLength > maxFieldNameLength) {				
				throw new DBFException("NON-ASCII field name:" + field.getName() + " exceds allowed length of " + maxFieldNameLength + "(" + maxFieldNameLength+")");
			}
				
		}
		this.header.fieldArray = new DBFField[fields.length];
		for (int i = 0; i < fields.length; i++) {
			this.header.fieldArray[i] = new DBFField(fields[i]);
		}
		
		
		try {
			if (this.raf != null && this.raf.length() == 0) {
				// this is a new/non-existent file. So write header before proceeding
				this.header.write(this.raf);
			}
		} catch (IOException e) {
			throw new DBFException("Error accesing file:" + e.getMessage(), e);
		}
	}

	/**
	 * Add a record.
	 * @param values fields of the record
	 */
	public void addRecord(Object[] values) {
		if (this.closed) {
			throw new IllegalStateException("You can add records a closed DBFWriter");
		}
		if (this.header.fieldArray == null) {
			throw new DBFException("Fields should be set before adding records");
		}

		if (values == null) {
			throw new DBFException("Null cannot be added as row");
		}

		if (values.length != this.header.fieldArray.length) {
			throw new DBFException("Invalid record. Invalid number of fields in row");
		}

		for (int i = 0; i < this.header.fieldArray.length; i++) {
			Object value = values[i];
			if (value == null) {
				continue;
			}

			switch (this.header.fieldArray[i].getType()) {

			case CHARACTER:
				if (!(value instanceof String)) {
					throw new DBFException("Invalid value for field " + i + ":" + value);
				}
				break;

			case LOGICAL:
				if (!(value instanceof Boolean)) {
					throw new DBFException("Invalid value for field " + i + ":" + value);
				}
				break;

			case DATE:
				if (!(value instanceof Date)) {
					throw new DBFException("Invalid value for field " + i + ":" + value);
				}
				break;
			case NUMERIC:
			case FLOATING_POINT:
				if (!(value instanceof Number)) {
					throw new DBFException("Invalid value for field " + i + ":" + value);
				}
				break;
			case LONG:
			case AUTOINCREMENT:
				if (!(value instanceof Number)) {
					throw new DBFException("Invalid value for field " + i + ":"+ value);
				}
				break;
			case TIMESTAMP:
			case TIMESTAMP_DBASE7:
				if (!(value instanceof Date) && !(value instanceof Calendar)) {
					throw new DBFException("Invalid value for field " + i + ":" + value);
				}
				break;
			default:
				throw new DBFException("Unsupported writting of field type " + i + " "
						+ this.header.fieldArray[i].getType());
			}

		}

		if (this.raf == null) {
			this.v_records.add(values);
		} else {
			try {
				writeRecord(this.raf, values);
				this.recordCount++;
			} catch (IOException e) {
				throw new DBFException("Error occured while writing record. " + e.getMessage(), e);
			}
		}
	}



	private void writeToStream(OutputStream out) {
		try {

			DataOutputStream outStream = new DataOutputStream(out);
			this.header.numberOfRecords = this.v_records.size();
			this.header.write(outStream);

			/* Now write all the records */
			for (Object[] record : this.v_records) {
				writeRecord(outStream, record);
			}

			outStream.write(END_OF_DATA);
			outStream.flush();

		} catch (IOException e) {
			throw new DBFException(e.getMessage(), e);
		}
	}
	/**
	 * In sync mode, write the header and close the file
	 */
	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		if (this.raf != null) {
			/*
			 * everything is written already. just update the header for
			 * record count and the END_OF_DATA mark
			 */
			try {
				this.header.numberOfRecords = this.recordCount;
				this.raf.seek(0);
				this.header.write(this.raf);
				this.raf.seek(this.raf.length());
				this.raf.writeByte(END_OF_DATA);
			}
			catch (IOException e) {
				throw new DBFException(e.getMessage(), e);
			}
			finally {
				DBFUtils.close(this.raf);
			}
		}
		else if (this.outputStream != null) {
			try {
				writeToStream(this.outputStream);
			}
			finally {
				DBFUtils.close(this.outputStream);
			}
		}

	}


	private void writeRecord(DataOutput dataOutput, Object[] objectArray) throws IOException {
		dataOutput.write((byte) ' ');
		for (int j = 0; j < this.header.fieldArray.length; j++) {
			/* iterate throught fields */
			switch (this.header.fieldArray[j].getType()) {

			case CHARACTER:
				String strValue = "";
				if (objectArray[j] != null) {
					strValue = objectArray[j].toString();
				}
				dataOutput.write(DBFUtils.textPadding(strValue, getCharset(), this.header.fieldArray[j].getLength(), DBFAlignment.LEFT, (byte) ' '));

				break;

			case DATE:
				if (objectArray[j] != null) {
					GregorianCalendar calendar = new GregorianCalendar();
					calendar.setTime((Date) objectArray[j]);
					dataOutput.write(DBFUtils.textPadding(String.valueOf(calendar.get(Calendar.YEAR)),
							DBFStandardCharsets.US_ASCII, 4, DBFAlignment.RIGHT, (byte) '0'));
					dataOutput.write(DBFUtils.textPadding(String.valueOf(calendar.get(Calendar.MONTH) + 1),
							DBFStandardCharsets.US_ASCII, 2, DBFAlignment.RIGHT, (byte) '0'));
					dataOutput.write(DBFUtils.textPadding(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)),
							DBFStandardCharsets.US_ASCII, 2, DBFAlignment.RIGHT, (byte) '0'));
				} else {
					dataOutput.write("        ".getBytes(DBFStandardCharsets.US_ASCII));
				}

				break;
			case NUMERIC:
			case FLOATING_POINT:

				if (objectArray[j] != null) {
					dataOutput.write(DBFUtils.doubleFormating((Number) objectArray[j], getCharset(),
							this.header.fieldArray[j].getLength(), this.header.fieldArray[j].getDecimalCount()));
				} else {
					dataOutput.write(DBFUtils.textPadding(" ", getCharset(), this.header.fieldArray[j].getLength(), DBFAlignment.RIGHT, (byte) ' '));
				}

				break;

			case LOGICAL:

				if (objectArray[j] instanceof Boolean) {
					if (((Boolean) objectArray[j]).booleanValue()) {
						dataOutput.write((byte) 'T');
					} else {
						dataOutput.write((byte) 'F');
					}
				} else {
					dataOutput.write((byte) '?');
				}

				break;
				
			case LONG:
			case AUTOINCREMENT:
				if (objectArray[j] != null) {
					dataOutput.write(DBFUtils.littleEndian(((Number) objectArray[j]).intValue()));
				}
				else {
					dataOutput.write(0);
				}
				break;
			
			case TIMESTAMP:
			case TIMESTAMP_DBASE7:
				Calendar c = Calendar.getInstance();
				if (objectArray[j] instanceof Calendar) {
					c = (Calendar) objectArray[j];
				}
				if (objectArray[j] instanceof Date) {
					c.setTime((Date) objectArray[j]);
				}
				
				int millis = 
					c.get(Calendar.HOUR_OF_DAY) * 3600000 + 
					c.get(Calendar.MINUTE) * 60000 + 
					c.get(Calendar.SECOND) * 1000 + 
					c.get(Calendar.MILLISECOND);
				
				// 8 bytes - two longs, 
				// first for date, second for time.  The date is the number of days since  01/01/4713 BC. 
				// Time is hours * 3600000L + minutes * 60000L + Seconds * 1000L
					
				int days = (int) 
						(((c.getTimeInMillis() - millis) / 
						MILLISECS_PER_DAY) - (TIME_MILLIS_1_1_4713_BC / MILLISECS_PER_DAY)); 
				
				dataOutput.writeInt(DBFUtils.littleEndian(days));
				dataOutput.writeInt(DBFUtils.littleEndian(millis));

				
				break;

			default:
				throw new DBFException("Unknown field type " + this.header.fieldArray[j].getType());
			}
		}
	}

	/**
	 * Check if the writer is closed
	 * @return true if already closed
	 */
	protected boolean isClosed() {
		return this.closed;
	}

	/**
	 * Get de underlying RandomAccessFile. It can be null if OutputStream constructor is used.
	 * @return the underlying RandomAccessFile
	 */
	protected RandomAccessFile getRamdonAccessFile() {
		return this.raf;
	}


	/**
	 * Writes the set data to the OutputStream.
	 * @param out the output stream
	 * @deprecated use {@link #DBFWriter(OutputStream)} constructor and call close
	 */
	@Deprecated
	public void write(OutputStream out) {
		if (this.raf == null) {
			writeToStream(out);
		}
	}

	/**
	 * In sync mode, write the header and close the file
	 * @deprecated use {@link #close()}
	 */
	@Deprecated
	public void write()  {
		this.close();
	}

}
