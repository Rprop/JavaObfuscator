/* Generated by: ParserGeneratorCC: Do not edit this line. StreamProvider.java Version 1.1 */
/* ParserGeneratorCCOptions:KEEP_LINE_COLUMN=true */
/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2020 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */
package com.github.javaparser;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * NOTE : This generated class can be safely deleted if installing in a GWT installation (use StringProvider instead)
 */
public class StreamProvider implements Provider
{
	private Reader m_aReader;
	
  @Deprecated
  public StreamProvider(final InputStream stream, final String charsetName) throws IOException
  {
    this (new BufferedReader (new InputStreamReader (stream, charsetName)));
  }
  
  public StreamProvider(final InputStream stream, final java.nio.charset.Charset charset)
  {
    this (new BufferedReader (new InputStreamReader (stream, charset)));
  }

  public StreamProvider (final Reader reader)
  {
    m_aReader = reader;
  }

  public int read (final char[] aDest, final int nOfs, final int nLen) throws IOException
  {
    int result = m_aReader.read(aDest, nOfs, nLen);

    /* CBA -- Added 2014/03/29 -- 
       This logic allows the generated Java code to be easily translated to C# (via sharpen) -
       as in C# 0 represents end of file, and in Java, -1 represents end of file
       See : http://msdn.microsoft.com/en-us/library/9kstw824(v=vs.110).aspx
       ** Technically, this is not required for java but the overhead is extremely low compared to the code generation benefits.
	   */
    if (result == 0)
      if (nOfs < aDest.length && nLen > 0)
        result = -1;
	   
    return result;
	}

	public void close () throws IOException
	{
	  if (m_aReader != null)
      m_aReader.close();
	}
}
/* ParserGeneratorCC - OriginalChecksum=b9eca802845867da8b35946746967e8e (do not edit this line) */
