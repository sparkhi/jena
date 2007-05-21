/*
 * (c) Copyright 2006, 2007 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package com.hp.hpl.jena.sdb.layout2.hash;

import static com.hp.hpl.jena.sdb.sql.SQLUtils.sqlStr;

import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hp.hpl.jena.sdb.layout2.TableDescNodes;
import com.hp.hpl.jena.sdb.sql.SDBConnection;
import com.hp.hpl.jena.sdb.sql.TableUtils;

/** Interface to setting up the bulk loader environment.
 * 
 * @author Andy Seaborne
 * @version $Id: LoaderHSQL.java,v 1.1 2006/04/21 16:53:32 andy_seaborne Exp $
 */

public class LoaderHashSQLServer extends LoaderHashLJ
{
	private static Log log = LogFactory.getLog(LoaderHashSQLServer.class);

    public LoaderHashSQLServer(SDBConnection connection) { super(connection) ; }
    
    public void createLoaderTable() throws SQLException
    {
        if (!TableUtils.hasTable(this, getNodeLoader()))
        	connection().exec(sqlStr(
        			"CREATE TABLE " + getNodeLoader(),
                    "(",
                    "  hash BIGINT NOT NULL ,",
                    "  lex TEXT NOT NULL ,",
                    "  lang VARCHAR(10) NOT NULL ,",
                    "  datatype VARCHAR("+ TableDescNodes.DatatypeUriLength+ ") NOT NULL ,",
                    "  type INT NOT NULL ,",
                    "  vInt INT,",
                    "  vDouble DOUBLE PRECISION,",
                    "  vDateTime DATETIME",
                    ") "
        	));
        
        if (!TableUtils.hasTable(this, getTripleLoader()))
        	connection().exec(sqlStr(
        			"CREATE TABLE " + getTripleLoader(),
        			"(",
        			"  s BIGINT NOT NULL,",
        			"  p BIGINT NOT NULL,",
        			"  o BIGINT NOT NULL",
        			") "
        	));
    }
    
    
    @Override
	protected String getNodeLoader() {
		// In SQL Server temp tables start with a #
		return "#"+super.getNodeLoader();
	}

	@Override
	protected String getTripleLoader() {
		// In SQL Server temp tables start with a #
		return "#"+super.getTripleLoader();
	}

}

/*
 * (c) Copyright 2006, 2007 Hewlett-Packard Development Company, LP
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
