/*
    JSPWiki - a JSP-based WikiWiki clone.

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.    
 */
package org.apache.wiki.search;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.time.StopWatch;
import org.apache.wiki.InternalWikiException;
import org.apache.wiki.NoRequiredPropertyException;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.api.FilterException;
import org.apache.wiki.api.WikiException;
import org.apache.wiki.api.WikiPage;
import org.apache.wiki.content.PageNotFoundException;
import org.apache.wiki.content.WikiPath;
import org.apache.wiki.event.*;
import org.apache.wiki.log.Logger;
import org.apache.wiki.log.LoggerFactory;
import org.apache.wiki.modules.InternalModule;
import org.apache.wiki.parser.MarkupParser;
import org.apache.wiki.providers.ProviderException;
import org.apache.wiki.rpc.RPCCallable;
import org.apache.wiki.rpc.json.JSONRPCManager;
import org.apache.wiki.util.ClassUtil;
import org.apache.wiki.util.TextUtil;


/**
 *  Manages searching the Wiki.
 *
 *  @since 2.2.21.
 */

public class SearchManager
    implements InternalModule, WikiEventListener
{
    private static final Logger log = LoggerFactory.getLogger(SearchManager.class);

    private static final String DEFAULT_SEARCHPROVIDER  = "org.apache.wiki.search.LuceneSearchProvider";
    
    /** Old option, now deprecated. */
    private static final String PROP_USE_LUCENE        = "jspwiki.useLucene";
    
    /**
     *  Property name for setting the search provider. Value is <tt>{@value}</tt>.
     */
    public static final String PROP_SEARCHPROVIDER     = "jspwiki.searchProvider";

    private SearchProvider    m_searchProvider = null;

    private WikiEngine m_engine = null;

    /**
     *  The name of the JSON object that manages search.
     */
    public static final String JSON_SEARCH = "search";

    /**
     *  Creates a new SearchManager.
     *  
     *  @throws WikiException If it cannot be instantiated.
     */
    public SearchManager()
        throws WikiException
    {
        // Do nothing, really
        super();
    }

    /**
     *  Provides a JSON RPC API to the JSPWiki Search Engine.
     */
    public class JSONSearch implements RPCCallable
    {
        /**
         *  Provides a list of suggestions to use for a page name.
         *  Currently the algorithm just looks into the value parameter,
         *  and returns all page names from that.
         *
         *  @param wikiName the page name
         *  @param maxLength maximum number of suggestions
         *  @return the suggestions
         */
        public List<String> getSuggestions( String wikiName, int maxLength )
        {
            StopWatch sw = new StopWatch();
            sw.start();
            List<String> list = new ArrayList<String>(maxLength);

            if( wikiName.length() > 0 )
            {
                
                // split pagename and attachment filename
                String filename = "";
                int pos = wikiName.indexOf("/");
                if( pos >= 0 ) 
                {
                    filename = wikiName.substring( pos ).toLowerCase();
                    wikiName = wikiName.substring( 0, pos );
                }
                
                String cleanWikiName = MarkupParser.cleanLink(wikiName).toLowerCase() + filename;

                String oldStyleName = MarkupParser.wikifyLink(wikiName).toLowerCase() + filename;

                Set<String> allPages;
                try
                {
                    allPages = m_engine.getReferenceManager().findCreated();
                }
                catch( ProviderException e )
                {
                    // FIXME: THis is probably not very smart.
                    allPages = new TreeSet<String>();
                }

                int counter = 0;
                for( Iterator<String> i = allPages.iterator(); i.hasNext() && counter < maxLength; )
                {
                    String p = i.next();
                    String pp = p.toLowerCase();
                    if( pp.startsWith( cleanWikiName) || pp.startsWith( oldStyleName ) )
                    {
                        list.add( p );
                        counter++;
                    }
                }
            }

            sw.stop();
            if( log.isDebugEnabled() ) log.debug("Suggestion request for "+wikiName+" done in "+sw);
            return list;
        }

        /**
         *  Performs a full search of pages.
         *
         *  @param searchString The query string
         *  @param maxLength How many hits to return
         *  @return the pages found
         */
        public List<HashMap<String,Object>> findPages( String searchString, int maxLength )
        {
            StopWatch sw = new StopWatch();
            sw.start();

            List<HashMap<String,Object>> list = new ArrayList<HashMap<String,Object>>(maxLength);

            if( searchString.length() > 0 )
            {
                try
                {
                    Collection<SearchResult> c;

                    if( m_searchProvider instanceof LuceneSearchProvider )
                        c = ((LuceneSearchProvider)m_searchProvider).findPages( searchString, 0 );
                    else
                        c = m_searchProvider.findPages( searchString );

                    int count = 0;
                    for( Iterator<SearchResult> i = c.iterator(); i.hasNext() && count < maxLength; count++ )
                    {
                        SearchResult sr = i.next();
                        HashMap<String,Object> hm = new HashMap<String,Object>();
                        hm.put( "page", sr.getPage().getName() );
                        hm.put( "score", sr.getScore() );
                        list.add( hm );
                    }
                }
                catch(Exception e)
                {
                    log.info("AJAX search failed; ",e);
                }
            }

            sw.stop();
            if( log.isDebugEnabled() ) log.debug("AJAX search complete in "+sw);
            return list;
        }
    }

    /**
     *  This particular method starts off indexing and all sorts of various activities,
     *  so you need to run this last, after things are done.
     *
     * @param engine the wiki engine
     * @param properties the properties used to initialize the wiki engine
     * @throws FilterException if the search provider failed to initialize
     */
    public void initialize(WikiEngine engine, Properties properties)
        throws FilterException
    {
        m_engine = engine;

        loadSearchProvider(properties);

        // Make sure we catch any page add/save/rename events
        WikiEventManager.addWikiEventListener( engine.getContentManager(), this );

        JSONRPCManager.registerGlobalObject( JSON_SEARCH, new JSONSearch() );
        
        try
        {
            m_searchProvider.initialize(engine, properties);
        }
        catch (NoRequiredPropertyException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void loadSearchProvider(Properties properties)
    {
        //
        // See if we're using Lucene, and if so, ensure that its
        // index directory is up to date.
        //
        String useLucene = properties.getProperty(PROP_USE_LUCENE);

        // FIXME: Obsolete, remove, or change logic to first load searchProvder?
        // If the old jspwiki.useLucene property is set we use that instead of the searchProvider class.
        if( useLucene != null )
        {
            log.info( PROP_USE_LUCENE+" is deprecated; please use "+PROP_SEARCHPROVIDER+"=<your search provider> instead." );
            if( TextUtil.isPositive( useLucene ) )
            {
                m_searchProvider = new LuceneSearchProvider();
            }
            else
            {
                m_searchProvider = new BasicSearchProvider();
            }
            log.debug("useLucene was set, loading search provider " + m_searchProvider);
            return;
        }

        String providerClassName = properties.getProperty( PROP_SEARCHPROVIDER,
                                                           DEFAULT_SEARCHPROVIDER );

        try
        {
            Class<?> providerClass = ClassUtil.findClass( "org.apache.wiki.search", providerClassName );
            m_searchProvider = (SearchProvider)providerClass.newInstance();
        }
        catch( ClassNotFoundException e )
        {
            log.warn("Failed loading SearchProvider, will use BasicSearchProvider.", e);
        }
        catch( InstantiationException e )
        {
            log.warn("Failed loading SearchProvider, will use BasicSearchProvider.", e);
        }
        catch( IllegalAccessException e )
        {
            log.warn("Failed loading SearchProvider, will use BasicSearchProvider.", e);
        }

        if( null == m_searchProvider )
        {
            // FIXME: Make a static with the default search provider
            m_searchProvider = new BasicSearchProvider();
        }
        log.debug("Loaded search provider " + m_searchProvider);
    }

    /**
     *  Returns the SearchProvider used.
     *  
     *  @return The current SearchProvider.
     */
    protected SearchProvider getSearchProvider()
    {
        return m_searchProvider;
    }

    /**
     *  Sends a search to the current search provider. The query is is whatever native format
     *  the query engine wants to use.
     *
     * @param query the query string. A value of <code>null</code> is treated
     * as an zero-length query string.
     * @return A collection of WikiPages that matched.
     * @throws ProviderException If the provider fails and a search cannot be completed.
     * @throws IOException If something else goes wrong.
     */
    public List<SearchResult> findPages( String query )
        throws ProviderException, IOException
    {
        if( query == null ) query = "";
        List<SearchResult> c = m_searchProvider.findPages( query );
        return c;
    }

    /**
     *  Removes the page from the search cache (if any).
     *  @param page  The page to remove
     */
    private void removePage(WikiPage page)
    {
        try
        {
            m_searchProvider.pageRemoved(page);
        }
        catch( ProviderException e )
        {
            log.error("Unable to remove page from Search index",e);
        }
    }

    /**
     *   Forces the reindex of the given page.
     *
     *   @param page The page.
     */
    public void reindexPage(WikiPage page) throws ProviderException
    {
        m_searchProvider.reindexPage(page);
    }

    /**
     *  If the page has been deleted, removes it from the index.
     *  
     *  @param event {@inheritDoc}
     */
    public void actionPerformed(WikiEvent event)
    {
        if ( !(event instanceof WikiPageEvent ) )
        {
           return; 
        }
        
        WikiPath pageName = ((WikiPageEvent) event).getPath();
        switch ( event.getType() )
        {
            // If page was deleted, remove it from the index
            case ( ContentEvent.NODE_DELETE_REQUEST ):
            {
                try
                {
                    WikiPage p = m_engine.getPage( pageName );
                    removePage( p );
                }
                catch( PageNotFoundException e )
                {
                    throw new InternalWikiException("Page removed already!?! Page="+pageName);
                }
                catch( ProviderException e ) 
                {
                    log.info( "Could not reindex page " + pageName, e );
                    e.printStackTrace();
                }
            }
            
            // If page was saved, reindex it
            case ( ContentEvent.NODE_SAVED ):
            {
                //
                //  Makes sure that we're indexing the latest version of this
                //  page.
                //
                WikiPage p;
                try
                {
                    p = m_engine.getPage( pageName );
                    reindexPage( p );
                }
                catch( PageNotFoundException e )
                {
                    // Swallow quietly; something went wrong but no point making fuss about it.
                }
                catch( ProviderException e )
                {
                    log.info( "Could not reindex page " + pageName, e );
                    e.printStackTrace();
                }
            }
        }
    }

}