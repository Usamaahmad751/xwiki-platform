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
package org.xwiki.wiki.user.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.wiki.descriptor.WikiDescriptor;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;
import org.xwiki.wiki.user.MemberCandidacy;
import org.xwiki.wiki.user.MembershipType;
import org.xwiki.wiki.user.WikiUserManager;
import org.xwiki.wiki.user.WikiUserManagerException;
import org.xwiki.wiki.user.WikiUserPropertyGroup;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation for {@link WikiUserManager}.
 *
 * @version $Id$
 * @since 5.3M2
 */
@Component
@Singleton
public class DefaultWikiUserManager implements WikiUserManager
{
    private static final String GROUP_CLASS_NAME = "XWikiGroups";

    private static final String GROUP_CLASS_MEMBER_FIELD = "member";

    private static final String CANDIDACY_CLASS_NAME = "WorkspaceCandidateMemberClass";

    private static final String CANDIDACY_CLASS_SPACE = "XWiki";

    private static final String CANDIDACY_CLASS_TYPE_FIELD = "type";

    private static final String CANDIDACY_CLASS_STATUS_FIELD = "status";

    private static final String CANDIDACY_CLASS_USER_FIELD = "userName";

    private static final String CANDIDACY_CLASS_USER_COMMENT_FIELD = "userComment";

    private static final String CANDIDACY_CLASS_ADMIN_FIELD = "reviewer";

    private static final String CANDIDACY_CLASS_ADMIN_COMMENT_FIELD = "reviewerComment";

    private static final String CANDIDACY_CLASS_ADMIN_PRIVATE_COMMENT_FIELD = "reviewerPrivateComment";

    private static final String CANDIDACY_CLASS_DATE_OF_CREATION_FIELD = "date";

    private static final String CANDIDACY_CLASS_DATE_OF_CLOSURE_FIELD = "resolutionDate";

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    private WikiUserPropertyGroup getPropertyGroup(String wikiId) throws WikiManagerException
    {
        WikiDescriptor descriptor = wikiDescriptorManager.getById(wikiId);
        return (WikiUserPropertyGroup) descriptor.getPropertyGroup(WikiUserPropertyGroupProvider.GROUP_NAME);
    }

    @Override
    public boolean hasLocalUsersEnabled(String wikiId) throws WikiManagerException
    {
        return getPropertyGroup(wikiId).hasLocalUsersEnabled();
    }

    @Override
    public void enableLocalUsers(String wikiId, boolean enable) throws WikiManagerException
    {
        getPropertyGroup(wikiId).enableLocalUsers(enable);
        wikiDescriptorManager.saveDescriptor(wikiDescriptorManager.getById(wikiId));
    }

    @Override
    public MembershipType getMembershipType(String wikiId) throws WikiManagerException
    {
        return getPropertyGroup(wikiId).getMembershipType();
    }

    @Override
    public void setMembershipType(String wikiId, MembershipType type) throws WikiManagerException
    {
        WikiDescriptor descriptor = wikiDescriptorManager.getById(wikiId);
        WikiUserPropertyGroup group =
                (WikiUserPropertyGroup) descriptor.getPropertyGroup(WikiUserPropertyGroupProvider.GROUP_NAME);
        group.setMembershypType(type);
        wikiDescriptorManager.saveDescriptor(descriptor);
    }

    @Override
    public Collection<String> getLocalUsers(String wikiId) throws WikiUserManagerException
    {
        // TODO: Implement this method. This is not urgent because the UI currently does all the job.
        return null;
    }

    private XWikiDocument getMembersGroupDocument(String wikiId) throws WikiUserManagerException
    {
        // Reference to the document
        DocumentReference memberGroupReference = new DocumentReference(wikiId, XWiki.SYSTEM_SPACE, "XWikiAllGroup");

        // Get the document
        try {
            XWikiContext xcontext = xcontextProvider.get();
            XWiki xwiki = xcontext.getWiki();
            return xwiki.getDocument(memberGroupReference, xcontext);
        } catch (XWikiException e) {
            throw new WikiUserManagerException(String.format("Fail to load the member group document [%s].",
                    memberGroupReference.toString()), e);
        }
    }

    private void saveGroupDocument(XWikiDocument document, String message) throws WikiUserManagerException {
        // Save the document
        XWikiContext xcontext = xcontextProvider.get();
        XWiki xwiki = xcontext.getWiki();
        try {
            xwiki.saveDocument(document, message, xcontext);
        } catch (XWikiException e) {
            throw new WikiUserManagerException("Fail to save the member group", e);
        }
    }

    @Override
    public Collection<String> getMembers(String wikiId) throws WikiUserManagerException
    {
        List<String> members = new ArrayList<String>();

        XWikiDocument groupDoc = getMembersGroupDocument(wikiId);
        DocumentReference classReference = new DocumentReference(wikiId, XWiki.SYSTEM_SPACE, GROUP_CLASS_NAME);
        for (BaseObject object : groupDoc.getXObjects(classReference)) {
            String member = object.getStringValue(GROUP_CLASS_MEMBER_FIELD);
            if (!member.isEmpty() && !members.contains(member)) {
                members.add(member);
            }
        }

        return members;
    }

    @Override
    public void addMember(String userId, String wikiId) throws WikiUserManagerException
    {
        Collection<String> members = getMembers(wikiId);
        if (members.contains(userId)) {
            // Nothing to do !
            return;
        }

        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(wikiId);

        // Add a member object
        XWikiContext xcontext = xcontextProvider.get();
        DocumentReference classReference = new DocumentReference(wikiId, XWiki.SYSTEM_SPACE, GROUP_CLASS_NAME);
        try {
            int objectNumber = groupDoc.createXObject(classReference, xcontext);
            BaseObject object = groupDoc.getXObject(classReference, objectNumber);
            object.set(GROUP_CLASS_MEMBER_FIELD, userId, xcontext);
        } catch (XWikiException e) {
            throw new WikiUserManagerException("Fail to add a member to the group", e);
        }

        // Save the document
        saveGroupDocument(groupDoc, String.format("Add [%] to the group.", userId));
    }

    @Override
    public void removeMember(String userId, String wikiId) throws WikiUserManagerException
    {
        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(wikiId);

        // Get the member objects to remove
        DocumentReference classReference = new DocumentReference(wikiId, XWiki.SYSTEM_SPACE, GROUP_CLASS_NAME);
        List<BaseObject> objectsToRemove = new ArrayList<BaseObject>();
        for (BaseObject object : groupDoc.getXObjects(classReference)) {
            String member = object.getStringValue(GROUP_CLASS_MEMBER_FIELD);
            if (userId.equals(member)) {
                objectsToRemove.add(object);
            }
        }

        // Remove them
        for (BaseObject object : objectsToRemove) {
            groupDoc.removeXObject(object);
        }

        // Save the document
        saveGroupDocument(groupDoc, String.format("Remove [%] from the group.", userId));
    }

    private MemberCandidacy readCandidacyFromObject(BaseObject object)
    {
        MemberCandidacy candidacy = new MemberCandidacy();

        candidacy.setId(object.getNumber());
        candidacy.setUserId(object.getStringValue(CANDIDACY_CLASS_USER_FIELD));
        candidacy.setUserComment(object.getLargeStringValue(CANDIDACY_CLASS_USER_COMMENT_FIELD));
        candidacy.setAdminId(object.getStringValue(CANDIDACY_CLASS_ADMIN_FIELD));
        candidacy.setAdminComment(object.getLargeStringValue(CANDIDACY_CLASS_ADMIN_COMMENT_FIELD));
        candidacy.setAdminPrivateComment(object.getLargeStringValue(CANDIDACY_CLASS_ADMIN_PRIVATE_COMMENT_FIELD));
        candidacy.setStatus(
                MemberCandidacy.Status.valueOf(object.getStringValue(CANDIDACY_CLASS_STATUS_FIELD).toUpperCase()));
        candidacy.setType(
                MemberCandidacy.CandidateType.valueOf(object.getStringValue(CANDIDACY_CLASS_STATUS_FIELD).toUpperCase())
        );
        candidacy.setDateOfCreation(object.getDateValue(CANDIDACY_CLASS_DATE_OF_CREATION_FIELD));
        candidacy.setDateOfCreation(object.getDateValue(CANDIDACY_CLASS_DATE_OF_CLOSURE_FIELD));

        return candidacy;
    }

    private Collection<MemberCandidacy> getAllMemberCandidacies(String wikiId, MemberCandidacy.CandidateType type)
        throws WikiUserManagerException
    {
        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(wikiId);

        // Collect all the candidacy of the good type
        Collection<MemberCandidacy> candidacies = new ArrayList<MemberCandidacy>();
        String typeString = type.name().toLowerCase();
        DocumentReference candidateClassReference = new DocumentReference(wikiId, CANDIDACY_CLASS_SPACE,
                CANDIDACY_CLASS_NAME);
        for (BaseObject object : groupDoc.getXObjects(candidateClassReference)) {
            if (object.getStringValue(CANDIDACY_CLASS_TYPE_FIELD).equals(typeString)) {
                candidacies.add(readCandidacyFromObject(object));
            }
        }

        return candidacies;
    }

    @Override
    public Collection<MemberCandidacy> getAllInvitations(String wikiId) throws WikiUserManagerException
    {
        return getAllMemberCandidacies(wikiId, MemberCandidacy.CandidateType.INVITATION);
    }

    @Override
    public Collection<MemberCandidacy> getAllRequests(String wikiId) throws WikiUserManagerException
    {
        return getAllMemberCandidacies(wikiId, MemberCandidacy.CandidateType.REQUEST);
    }

    @Override
    public MemberCandidacy getCandidacy(String wikiId, int candidacyId) throws WikiUserManagerException
    {
        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(wikiId);

        // Get the candidacy
        DocumentReference candidateClassReference = new DocumentReference(wikiId, CANDIDACY_CLASS_SPACE,
                CANDIDACY_CLASS_NAME);
        BaseObject object = groupDoc.getXObject(candidateClassReference, candidacyId);
        return readCandidacyFromObject(object);
    }

    @Override
    public MemberCandidacy askToJoin(String userId, String wikiId, String message)
        throws WikiUserManagerException
    {
        MemberCandidacy candidacy = new MemberCandidacy(wikiId, userId, MemberCandidacy.CandidateType.REQUEST);
        candidacy.setUserComment(message);

        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(wikiId);

        // Add a candidacy object
        XWikiContext xcontext = xcontextProvider.get();
        DocumentReference candidateClassReference = new DocumentReference(wikiId, CANDIDACY_CLASS_SPACE,
                CANDIDACY_CLASS_NAME);
        try {
            int objectNumber = groupDoc.createXObject(candidateClassReference, xcontext);
            candidacy.setId(objectNumber);
            BaseObject object = groupDoc.getXObject(candidateClassReference, objectNumber);
            object.setStringValue(CANDIDACY_CLASS_USER_FIELD, candidacy.getUserId());
            object.setLargeStringValue(CANDIDACY_CLASS_USER_COMMENT_FIELD, candidacy.getUserComment());
            object.setStringValue(CANDIDACY_CLASS_STATUS_FIELD, candidacy.getStatus().name().toLowerCase());
            object.setDateValue(CANDIDACY_CLASS_DATE_OF_CREATION_FIELD, candidacy.getDateOfCreation());
            object.setStringValue(CANDIDACY_CLASS_TYPE_FIELD, candidacy.getType().name().toLowerCase());
        } catch (XWikiException e) {
            throw new WikiUserManagerException("Failed to create a new join request.", e);
        }

        // Save the document
        saveGroupDocument(groupDoc, String.format("[%] asks to join the wiki.", userId));

        return candidacy;
    }

    @Override
    public void acceptRequest(MemberCandidacy request, String message, String privateComment)
        throws WikiUserManagerException
    {
        // Add the user to the members
        addMember(request.getUserId(), request.getWikiId());

        // Then, update the candidacy object
        XWikiContext xcontext = xcontextProvider.get();

        // Set the values
        request.setAdminId(xcontext.getUser());
        request.setAdminComment(message);
        request.setAdminPrivateComment(privateComment);
        request.setStatus(MemberCandidacy.Status.ACCEPTED);
        request.setDateOfClosure(new Date());

        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(request.getWikiId());

        // Get the candidacy object
        DocumentReference candidateClassReference = new DocumentReference(request.getWikiId(), CANDIDACY_CLASS_SPACE,
                CANDIDACY_CLASS_NAME);
        BaseObject object = groupDoc.getXObject(candidateClassReference, request.getId());

        // Set the new values
        object.setStringValue(CANDIDACY_CLASS_ADMIN_FIELD, request.getAdminId());
        object.setLargeStringValue(CANDIDACY_CLASS_ADMIN_COMMENT_FIELD, request.getAdminComment());
        object.setLargeStringValue(CANDIDACY_CLASS_ADMIN_PRIVATE_COMMENT_FIELD, request.getAdminPrivateComment());
        object.setDateValue(CANDIDACY_CLASS_DATE_OF_CLOSURE_FIELD, request.getDateOfClosure());
        object.setStringValue(CANDIDACY_CLASS_STATUS_FIELD, request.getStatus().name().toLowerCase());

        // Save the document
        saveGroupDocument(groupDoc, String.format("Accept join request from user [%]", request.getUserId()));
    }

    @Override
    public void refuseRequest(MemberCandidacy request, String message, String privateComment)
        throws WikiUserManagerException
    {
        // Update the candidacy object
        XWikiContext xcontext = xcontextProvider.get();

        // Set the values
        request.setAdminId(xcontext.getUser());
        request.setAdminComment(message);
        request.setAdminPrivateComment(privateComment);
        request.setStatus(MemberCandidacy.Status.REJECTED);
        request.setDateOfClosure(new Date());

        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(request.getWikiId());

        // Get the candidacy object
        DocumentReference candidateClassReference = new DocumentReference(request.getWikiId(), CANDIDACY_CLASS_SPACE,
                CANDIDACY_CLASS_NAME);
        BaseObject object = groupDoc.getXObject(candidateClassReference, request.getId());

        // Set the new values
        object.setStringValue(CANDIDACY_CLASS_ADMIN_FIELD, request.getAdminId());
        object.setLargeStringValue(CANDIDACY_CLASS_ADMIN_COMMENT_FIELD, request.getAdminComment());
        object.setLargeStringValue(CANDIDACY_CLASS_ADMIN_PRIVATE_COMMENT_FIELD, request.getAdminPrivateComment());
        object.setDateValue(CANDIDACY_CLASS_DATE_OF_CLOSURE_FIELD, request.getDateOfClosure());
        object.setStringValue(CANDIDACY_CLASS_STATUS_FIELD, request.getStatus().name().toLowerCase());

        // Save the document
        saveGroupDocument(groupDoc, String.format("Reject join request from user [%]", request.getUserId()));
    }

    @Override
    public MemberCandidacy invite(String userId, String wikiId, String message)
        throws WikiUserManagerException
    {
        XWikiContext xcontext = xcontextProvider.get();

        // Create the candidacy
        MemberCandidacy candidacy = new MemberCandidacy(wikiId, userId, MemberCandidacy.CandidateType.INVITATION);
        candidacy.setUserComment(message);
        candidacy.setAdminId(xcontext.getUser());

        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(wikiId);

        // Add a candidacy object
        DocumentReference candidateClassReference = new DocumentReference(wikiId, CANDIDACY_CLASS_SPACE,
                CANDIDACY_CLASS_NAME);
        try {
            int objectNumber = groupDoc.createXObject(candidateClassReference, xcontext);
            candidacy.setId(objectNumber);
            BaseObject object = groupDoc.getXObject(candidateClassReference, objectNumber);
            object.setStringValue(CANDIDACY_CLASS_USER_FIELD, candidacy.getUserId());
            object.setStringValue(CANDIDACY_CLASS_ADMIN_FIELD, candidacy.getAdminId());
            object.setLargeStringValue(CANDIDACY_CLASS_ADMIN_COMMENT_FIELD, message);
            object.setStringValue(CANDIDACY_CLASS_STATUS_FIELD, candidacy.getStatus().name().toLowerCase());
            object.setDateValue(CANDIDACY_CLASS_DATE_OF_CREATION_FIELD, candidacy.getDateOfCreation());
            object.setStringValue(CANDIDACY_CLASS_TYPE_FIELD, candidacy.getType().name().toLowerCase());
        } catch (XWikiException e) {
            throw new WikiUserManagerException("Failed to create a new invitation object.", e);
        }

        // Save the document
        saveGroupDocument(groupDoc, String.format("[%] is invited to join the wiki.", userId));

        return candidacy;
    }

    @Override
    public void acceptInvitation(MemberCandidacy invitation, String message) throws WikiUserManagerException
    {
        // Add the user to the members
        addMember(invitation.getUserId(), invitation.getWikiId());

        // Then, update the candidacy object
        XWikiContext xcontext = xcontextProvider.get();

        // Set the values
        invitation.setUserComment(message);
        invitation.setStatus(MemberCandidacy.Status.ACCEPTED);
        invitation.setDateOfClosure(new Date());

        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(invitation.getWikiId());

        // Get the candidacy object
        DocumentReference candidateClassReference = new DocumentReference(invitation.getWikiId(), CANDIDACY_CLASS_SPACE,
                CANDIDACY_CLASS_NAME);
        BaseObject object = groupDoc.getXObject(candidateClassReference, invitation.getId());

        // Set the new values
        object.setLargeStringValue(CANDIDACY_CLASS_USER_COMMENT_FIELD, invitation.getUserComment());
        object.setDateValue(CANDIDACY_CLASS_DATE_OF_CLOSURE_FIELD, invitation.getDateOfClosure());
        object.setStringValue(CANDIDACY_CLASS_STATUS_FIELD, invitation.getStatus().name().toLowerCase());

        // Save the document
        saveGroupDocument(groupDoc, String.format("User [%] has accepted to join the wiki. ", invitation.getUserId()));
    }

    @Override
    public void refuseInvitation(MemberCandidacy invitation, String message) throws WikiUserManagerException
    {
        // Update the candidacy object
        XWikiContext xcontext = xcontextProvider.get();

        // Set the values
        invitation.setUserComment(message);
        invitation.setStatus(MemberCandidacy.Status.REJECTED);
        invitation.setDateOfClosure(new Date());

        // Get the group document
        XWikiDocument groupDoc = getMembersGroupDocument(invitation.getWikiId());

        // Get the candidacy object
        DocumentReference candidateClassReference = new DocumentReference(invitation.getWikiId(), CANDIDACY_CLASS_SPACE,
                CANDIDACY_CLASS_NAME);
        BaseObject object = groupDoc.getXObject(candidateClassReference, invitation.getId());

        // Set the new values
        object.setLargeStringValue(CANDIDACY_CLASS_USER_COMMENT_FIELD, invitation.getUserComment());
        object.setDateValue(CANDIDACY_CLASS_DATE_OF_CLOSURE_FIELD, invitation.getDateOfClosure());
        object.setStringValue(CANDIDACY_CLASS_STATUS_FIELD, invitation.getStatus().name().toLowerCase());

        // Save the document
        saveGroupDocument(groupDoc, String.format("User [%] has rejected the invitation to join the wiki.",
                invitation.getUserId()));
    }
}
