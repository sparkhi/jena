/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seaborne.tdb2.store;

import java.util.Iterator ;

import org.apache.jena.atlas.iterator.Iter ;
import org.apache.jena.atlas.lib.Closeable ;
import org.apache.jena.atlas.lib.InternalErrorException ;
import org.apache.jena.atlas.lib.Sync ;
import org.apache.jena.atlas.lib.Tuple ;
import org.apache.jena.graph.Graph ;
import org.apache.jena.graph.GraphUtil ;
import org.apache.jena.graph.Node ;
import org.apache.jena.graph.Triple ;
import org.apache.jena.query.ReadWrite ;
import org.apache.jena.sparql.core.* ;
import org.apache.jena.sparql.engine.optimizer.reorder.ReorderTransformation ;
import org.seaborne.dboe.base.file.Location ;
import org.seaborne.dboe.transaction.TransactionalMonitor ;
import org.seaborne.dboe.transaction.txn.TransactionalSystem ;
import org.seaborne.tdb2.TDBException ;
import org.seaborne.tdb2.lib.NodeLib ;
import org.seaborne.tdb2.setup.StoreParams ;
import org.seaborne.tdb2.store.nodetupletable.NodeTupleTable ;
import org.seaborne.tdb2.sys.StoreConnection ;

/** This is the class that provides creates a dataset over the storage via
 *  TripleTable, QuadTable and prefixes.
 *  This is the coordination of the storage.
 *  @see DatasetGraphTxn for the Transaction form.
 */
final
public class DatasetGraphTDB extends DatasetGraphTriplesQuads
                             implements DatasetGraphTxn,
                             /*Old world*//*DatasetGraph,*/ Sync, Closeable
{
    // SWITCHING.
    private TripleTable tripleTable ;
    private QuadTable quadTable ;
    private DatasetPrefixStorage prefixes ;
    private Location location ;
    // SWITCHING.
    private final ReorderTransformation transform ;
    private StoreParams config ;
    
    private GraphTDB defaultGraphTDB ;
    private final boolean checkForChange = false ;
    private boolean closed = false ;
    private TransactionalSystem txnSystem ;

    public DatasetGraphTDB(TransactionalSystem txnSystem, 
                           TripleTable tripleTable, QuadTable quadTable, DatasetPrefixStorage prefixes,
                           ReorderTransformation transform, Location location, StoreParams params) {
        reset(txnSystem,tripleTable, quadTable, prefixes, location, params) ;
        this.transform = transform ;
        this.defaultGraphTDB = getDefaultGraphTDB() ;
    }

    public void reset(TransactionalSystem txnSystem, 
                      TripleTable tripleTable, QuadTable quadTable, DatasetPrefixStorage prefixes,
                      Location location, StoreParams params) {
        this.txnSystem = txnSystem ;
        this.tripleTable = tripleTable ;
        this.quadTable = quadTable ;
        this.prefixes = prefixes ;
        this.defaultGraphTDB = getDefaultGraphTDB() ;
        this.config = params ;
        this.location = location ;
    }
    
    public QuadTable getQuadTable()         { checkNotClosed() ; return quadTable ; }
    public TripleTable getTripleTable()     { checkNotClosed() ; return tripleTable ; }
    
    @Override
    protected Iterator<Quad> findInDftGraph(Node s, Node p, Node o) {
        checkNotClosed() ;
        return triples2quadsDftGraph(getTripleTable().find(s, p, o)) ;
    }

    @Override
    protected Iterator<Quad> findInSpecificNamedGraph(Node g, Node s, Node p, Node o) {
        checkNotClosed();
        return getQuadTable().find(g, s, p, o);
    }

    @Override
    protected Iterator<Quad> findInAnyNamedGraphs(Node s, Node p, Node o) {
        checkNotClosed();
        return getQuadTable().find(Node.ANY, s, p, o);
    }

    protected static Iterator<Quad> triples2quadsDftGraph(Iterator<Triple> iter)
    { return triples2quads(Quad.defaultGraphIRI, iter) ; }
 
    @Override
    protected void addToDftGraph(Node s, Node p, Node o) { 
        checkNotClosed() ;
        notifyAdd(null, s, p, o) ;
        getTripleTable().add(s,p,o) ;
    }

    @Override
    protected void addToNamedGraph(Node g, Node s, Node p, Node o) {
        checkNotClosed() ;
        notifyAdd(g, s, p, o) ;
        getQuadTable().add(g, s, p, o) ; 
    }

    @Override
    protected void deleteFromDftGraph(Node s, Node p, Node o) {
        checkNotClosed() ;
        notifyDelete(null, s, p, o) ;
        getTripleTable().delete(s, p, o) ;
    }

    @Override
    protected void deleteFromNamedGraph(Node g, Node s, Node p, Node o) {
        checkNotClosed() ;
        notifyDelete(g, s, p, o) ;
        getQuadTable().delete(g, s, p, o) ;
    }
    
    // XXX Optimize by integrating with add/delete operations.
    private final void notifyAdd(Node g, Node s, Node p, Node o) {
        if ( monitor == null )
            return ;
        QuadAction action = QuadAction.ADD ;
        if ( checkForChange ) {
            if ( contains(g,s,p,o) )
                action = QuadAction.NO_ADD ;
        }
        monitor.change(action, g, s, p, o);
    }

    private final void notifyDelete(Node g, Node s, Node p, Node o) {
        if ( monitor == null )
            return ;
        QuadAction action = QuadAction.DELETE ;
        if ( checkForChange ) {
            if ( ! contains(g,s,p,o) )
                action = QuadAction.NO_DELETE ;
        }
        monitor.change(action, g, s, p, o);
    }
    
    /** No-op. There is no need to close datasets.
     *  Use {@link StoreConnection#release(Location)}.
     *  (Datasets can not be reopened on MS Windows). 
     */
    @Override
    public void close() {
        if ( closed )
            return ;
        closed = true ;
    }
    
    private void checkNotClosed() {
        if ( closed )
            throw new TDBException("dataset closed") ;
    }
    
    /** Release resources.
     *  Do not call directly - this is called from StoreConnection.
     *  Use {@link StoreConnection#release(Location)}. 
     */
    public void shutdown() {
        tripleTable.close() ;
        quadTable.close() ;
        prefixes.close();
        // Which will cause reuse to throw exceptions early.
        tripleTable = null ;
        quadTable = null ;
        prefixes = null ;
        txnSystem.getTxnMgr().shutdown(); 
    }
    
    @Override
    // Empty graphs don't "exist" 
    public boolean containsGraph(Node graphNode) {
        checkNotClosed() ; 
        if ( Quad.isDefaultGraphExplicit(graphNode) || Quad.isUnionGraph(graphNode)  )
            return true ;
        return _containsGraph(graphNode) ; 
    }

    private boolean _containsGraph(Node graphNode) {
        // Have to look explicitly, which is a bit of a nuisance.
        // But does not normally happen for GRAPH <g> because that's rewritten to quads.
        // Only pattern with complex paths go via GRAPH. 
        Iterator<Tuple<NodeId>> x = quadTable.getNodeTupleTable().findAsNodeIds(graphNode, null, null, null) ;
        if ( x == null )
            return false ; 
        boolean result = x.hasNext() ;
        return result ;
    }
    
    @Override
    public void addGraph(Node graphName, Graph graph) {
        checkNotClosed() ; 
        removeGraph(graphName) ;
        GraphUtil.addInto(getGraph(graphName), graph) ;
    }

    @Override
    public final void removeGraph(Node graphName) {
        checkNotClosed() ; 
        deleteAny(graphName, Node.ANY, Node.ANY, Node.ANY) ;
    }

    @Override
    public Graph getDefaultGraph() {
        checkNotClosed() ; 
        return new GraphTDB(this, null) ; 
    }

    public GraphTDB getDefaultGraphTDB() {
        checkNotClosed();
        return (GraphTDB)getDefaultGraph();
    }

    @Override
    public Graph getGraph(Node graphNode) {
        checkNotClosed();
        return new GraphTDB(this, graphNode);
    }

    public GraphTDB getGraphTDB(Node graphNode) {
        checkNotClosed();
        return (GraphTDB)getGraph(graphNode);
    }

    public StoreParams getConfig() {
        checkNotClosed();
        return config;
    }

    public ReorderTransformation getReorderTransform() {
        checkNotClosed();
        return transform;
    }

    public DatasetPrefixStorage getPrefixes() {
        checkNotClosed();
        return prefixes;
    }

    @Override
    public Iterator<Node> listGraphNodes() {
        checkNotClosed();
        Iterator<Tuple<NodeId>> x = quadTable.getNodeTupleTable().findAll();
        Iterator<NodeId> z = Iter.iter(x).map(t -> t.get(0)).distinct();
        return NodeLib.nodes(quadTable.getNodeTupleTable().getNodeTable(), z);
    }

    @Override
    public long size() {
        checkNotClosed();
        return Iter.count(listGraphNodes());
    }

    @Override
    public boolean isEmpty() {
        checkNotClosed();
        return getTripleTable().isEmpty() && getQuadTable().isEmpty();
    }

    @Override
    public void clear() {
        checkNotClosed() ; 
        // Leave the node table alone.
        getTripleTable().clearTriples() ;
        getQuadTable().clearQuads() ;
    }
    
    public NodeTupleTable chooseNodeTupleTable(Node graphNode) {
        checkNotClosed() ; 

        if ( graphNode == null || Quad.isDefaultGraph(graphNode) )
            return getTripleTable().getNodeTupleTable() ;
        else
            // Includes Node.ANY and union graph
            return getQuadTable().getNodeTupleTable() ;
    }
    
    private static final int sliceSize = 1000 ;
    
    @Override
    public void deleteAny(Node g, Node s, Node p, Node o) {
        // Delete in batches.
        // That way, there is no active iterator when a delete
        // from the indexes happens.
        checkNotClosed() ;
        
        if ( monitor != null ) {
            // Need to do by nodes because we will log the deletes.
            super.deleteAny(g, s, p, o); 
            return ;
        }

        // Not logging - do by working as NodeIds.
        NodeTupleTable t = chooseNodeTupleTable(g) ;
        @SuppressWarnings("unchecked")
        Tuple<NodeId>[] array = (Tuple<NodeId>[])new Tuple<?>[sliceSize] ;

        while (true) { // Convert/cache s,p,o?
            // The Node Cache will catch these so don't worry unduely.
            Iterator<Tuple<NodeId>> iter = null ;
            if ( g == null )
                iter = t.findAsNodeIds(s, p, o) ;
            else
                iter = t.findAsNodeIds(g, s, p, o) ;

            if ( iter == null )
                return ;

            // Get a slice
            int len = 0 ;
            for (; len < sliceSize; len++) {
                if ( !iter.hasNext() )
                    break ;
                array[len] = iter.next() ;
            }
            
            // Delete the NodeId Tuples
            for (int i = 0; i < len; i++) {
                t.getTupleTable().delete(array[i]) ;
                array[i] = null ;
            }
            // Finished?
            if ( len < sliceSize )
                break ;
        }
    }
    
    public Location getLocation()       { return location ; }

    @Override
    public void sync() {
        checkNotClosed();
        tripleTable.sync();
        quadTable.sync();
        prefixes.sync();
    }
    
    @Override
    public void setDefaultGraph(Graph g) { 
        throw new UnsupportedOperationException("Can't set default graph on a TDB-backed dataset") ;
    }

    @Override
    public boolean isInTransaction() {
        return txnSystem.isInTransaction() ;
    }

    // txnSystem with monitor?
    @Override
    public void begin(ReadWrite readWrite) {
        if ( txnMonitor != null ) txnMonitor.startBegin(readWrite); 
        txnSystem.begin(readWrite) ;
        if ( txnMonitor != null ) txnMonitor.finishBegin(readWrite); 
    }

    @Override
    public boolean promote() {
        
        if ( txnMonitor != null ) txnMonitor.startPromote();
        try { 
            return txnSystem.promote() ;
        } finally { if ( txnMonitor != null ) txnMonitor.finishPromote(); }
    }

    @Override
    public void commit() {
        if ( txnMonitor != null ) txnMonitor.startCommit();
        txnSystem.commit() ;
        if ( txnMonitor != null ) txnMonitor.finishCommit();  
    }

    @Override
    public void abort() {
        if ( txnMonitor != null ) txnMonitor.startAbort() ; 
        txnSystem.abort() ;
        if ( txnMonitor != null ) txnMonitor.finishAbort() ;  
    }

    @Override
    public void end() {
        if ( txnMonitor != null ) txnMonitor.startEnd(); 
        txnSystem.end() ;
        if ( txnMonitor != null ) txnMonitor.finishEnd(); 
    }

    public TransactionalSystem getTxnSystem() {
        return txnSystem ;
    }

    // Watching changes (add, delete, deleteAny) 
    
    private DatasetChanges monitor = null ;
    public void setMonitor(DatasetChanges changes) {
        monitor = changes ;
    }

    public void removeMonitor(DatasetChanges changes) {
        if ( monitor != changes )
            throw new InternalErrorException() ;
        monitor = null ;
    }
    
    // Watching Transactional
    
    private TransactionalMonitor txnMonitor = null ;
    public void setTransactionalMonitor(TransactionalMonitor changes) {
        txnMonitor = changes ;
    }

    public void removeTransactionalMonitor(TransactionalMonitor changes) {
        if ( txnMonitor != changes )
            throw new InternalErrorException() ;
        txnMonitor = null ;
    }
    
}
