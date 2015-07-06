/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.api.messages;

import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a decrypted text secure message.
 */
public class TextSecureDataMessage {

  private final long                                 timestamp;
  private final Optional<List<TextSecureAttachment>> attachments;
  private final Optional<String>                     body;
  private final Optional<TextSecureGroup>            group;
  private final boolean                              endSession;

  /**
   * Construct a TextSecureMessage with a body and no attachments.
   *
   * @param timestamp The sent timestamp.
   * @param body The message contents.
   */
  public TextSecureDataMessage(long timestamp, String body) {
    this(timestamp, (List<TextSecureAttachment>)null, body);
  }

  public TextSecureDataMessage(final long timestamp, final TextSecureAttachment attachment, final String body) {
    this(timestamp, new LinkedList<TextSecureAttachment>() {{add(attachment);}}, body);
  }

  /**
   * Construct a TextSecureMessage with a body and list of attachments.
   *
   * @param timestamp The sent timestamp.
   * @param attachments The attachments.
   * @param body The message contents.
   */
  public TextSecureDataMessage(long timestamp, List<TextSecureAttachment> attachments, String body) {
    this(timestamp, null, attachments, body);
  }

  /**
   * Construct a TextSecure group message with attachments and body.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information.
   * @param attachments The attachments.
   * @param body The message contents.
   */
  public TextSecureDataMessage(long timestamp, TextSecureGroup group, List<TextSecureAttachment> attachments, String body) {
    this(timestamp, group, attachments, body, false);
  }

  /**
   * Construct a TextSecureMessage.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information (or null if none).
   * @param attachments The attachments (or null if none).
   * @param body The message contents.
   * @param endSession Flag indicating whether this message should close a session.
   */
  public TextSecureDataMessage(long timestamp, TextSecureGroup group, List<TextSecureAttachment> attachments, String body, boolean endSession) {
    this.timestamp   = timestamp;
    this.body        = Optional.fromNullable(body);
    this.group       = Optional.fromNullable(group);
    this.endSession  = endSession;

    if (attachments != null && !attachments.isEmpty()) {
      this.attachments = Optional.of(attachments);
    } else {
      this.attachments = Optional.absent();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * @return The message timestamp.
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * @return The message attachments (if any).
   */
  public Optional<List<TextSecureAttachment>> getAttachments() {
    return attachments;
  }

  /**
   * @return The message body (if any).
   */
  public Optional<String> getBody() {
    return body;
  }

  /**
   * @return The message group info (if any).
   */
  public Optional<TextSecureGroup> getGroupInfo() {
    return group;
  }

  public boolean isEndSession() {
    return endSession;
  }

  public boolean isGroupUpdate() {
    return group.isPresent() && group.get().getType() != TextSecureGroup.Type.DELIVER;
  }

  public static class Builder {

    private List<TextSecureAttachment> attachments = new LinkedList<>();
    private long                       timestamp;
    private TextSecureGroup            group;
    private String                     body;
    private boolean                    endSession;

    private Builder() {}

    public Builder withTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder asGroupMessage(TextSecureGroup group) {
      this.group = group;
      return this;
    }

    public Builder withAttachment(TextSecureAttachment attachment) {
      this.attachments.add(attachment);
      return this;
    }

    public Builder withAttachments(List<TextSecureAttachment> attachments) {
      this.attachments.addAll(attachments);
      return this;
    }

    public Builder withBody(String body) {
      this.body = body;
      return this;
    }

    public Builder asEndSessionMessage() {
      this.endSession = true;
      return this;
    }

    public Builder asEndSessionMessage(boolean endSession) {
      this.endSession = endSession;
      return this;
    }

    public TextSecureDataMessage build() {
      if (timestamp == 0) timestamp = System.currentTimeMillis();
      return new TextSecureDataMessage(timestamp, group, attachments, body, endSession);
    }
  }
}
