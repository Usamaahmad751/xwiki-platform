/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.internal.model.reference;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceProvider;

/**
 * The behavior is the same as for {@link CurrentEntityReferenceProvider} but with the following differences:
 * <ul>
 * <li>if the passed reference doesn't have a page name specified (or if it's empty) the value used is the default page
 * name (instead of the page name of the current document's reference).</li>
 * </ul>
 *
 * @version $Id$
 * @since 7.2M1
 */
@Component
@Named("currentmixed")
@Singleton
public class CurrentMixedEntityReferenceProvider extends CurrentEntityReferenceProvider
{
    @Inject
    private EntityReferenceProvider defaultProvider;

    @Override
    public EntityReference getDefaultReference(EntityType type)
    {
        EntityReference result;

        if (type == EntityType.DOCUMENT) {
            result = this.defaultProvider.getDefaultReference(EntityType.DOCUMENT);
        } else {
            result = super.getDefaultReference(type);
        }

        return result;
    }
}
