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

package org.apache.wiki.action;

import javax.servlet.http.HttpSession;

import net.sourceforge.stripes.validation.Validate;

import org.apache.wiki.api.WikiPage;
import org.apache.wiki.tags.BreadcrumbsTag;
import org.apache.wiki.tags.BreadcrumbsTag.FixedQueue;

/**
 * Abstract {@link WikiActionBean} subclass used by all ActionBeans that use and
 * process {@link org.apache.wiki.api.WikiPage} objects bound to the
 * <code>page</code> request parameter. In particular, this subclass contains
 * special processing logic that ensures that, the <code>page</code>
 * properties of this object and its related {@link org.apache.wiki.WikiContext}
 * are set to the same value. When {@link #setPage(WikiPage)} is called by, for
 * example, the Stripes controller, the underlying
 * {@link org.apache.wiki.ui.stripes.WikiActionBeanContext#setPage(WikiPage)}
 * method is called also.
 */
public class AbstractPageActionBean extends AbstractActionBean
{
    /**
     * Returns the WikiPage; defaults to <code>null</code>.
     * 
     * @return the page
     */
    public WikiPage getPage()
    {
        return getContext().getPage();
    }

    /**
     * Sets the WikiPage property for this ActionBean, and also sets the
     * WikiActionBeanContext's page property to the same value by calling
     * {@link org.apache.wiki.ui.stripes.WikiActionBeanContext#setPage(WikiPage)}.
     * Note that because of the {@link Validate} annotation, the
     * <code>page</code> field will be required by any executing event handler
     * method. To change this property, subclasses should override this method
     * and supply a <code>Validate( required = false )</code> annotation.
     * 
     * @param page the wiki page.
     */
    @Validate( required = true )
    public void setPage( WikiPage page )
    {
        getContext().setPage( page );
    }

    /**
     * Removes the deleted page from the breadCrumb trail
     * 
     * @param pageName the pageName to be removed from the breadcrumb
     */
    // FIXME: Is this in the right place? Shouldn't this be a static method in BreadcrumbsTag?
    void deleteFromBreadCrumb( String pageName )
    {
        HttpSession session = getContext().getRequest().getSession( false );
        if( session != null )
        {
            FixedQueue trail = (FixedQueue) session.getAttribute( BreadcrumbsTag.BREADCRUMBTRAIL_KEY );
            if( trail != null )
            {
                trail.removeItem( pageName );
                session.setAttribute( BreadcrumbsTag.BREADCRUMBTRAIL_KEY, trail );
            }
        }
    }

}
