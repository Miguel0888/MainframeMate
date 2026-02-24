package com.softwareag.naturalone.natural.paltransactions.external;

import com.softwareag.naturalone.natural.pal.external.IFileProperties;
import com.softwareag.naturalone.natural.pal.external.PalDate;
import com.softwareag.naturalone.natural.paltransactions.internal.PalTimeStamp;
import java.util.EnumSet;
import java.util.Set;

public class ObjectProperties implements IFileProperties {
   private final String name;
   private final String longName;
   private final String user;
   private final String gpUser;
   private final String codePage;
   private final int sourceSize;
   private final int gpSize;
   private final int kind;
   private final int type;
   private final int databaseId;
   private final int fnr;
   private final boolean isStructured;
   private final boolean isLinkedDdm;
   private final PalDate sourceDate;
   private final PalDate gpDate;
   private final PalDate accessDate;
   private final String internalLabelFirst;
   private final int lineNumberIncrement;
   private Set options;
   private final PalTimeStamp timeStamp;
   private final String baseLibrary;

   private ObjectProperties(Builder var1) {
      this.name = var1.name;
      this.longName = var1.longName;
      this.user = var1.user;
      this.gpUser = var1.gpUser;
      this.codePage = var1.codePage;
      this.sourceSize = var1.sourceSize;
      this.gpSize = var1.gpSize;
      this.kind = var1.natKind;
      this.type = var1.type;
      this.databaseId = var1.databaseId;
      this.fnr = var1.fnr;
      this.isStructured = var1.isStructured;
      this.isLinkedDdm = var1.isLinkedDdm;
      this.sourceDate = var1.sourceDate;
      this.gpDate = var1.gpDate;
      this.accessDate = var1.accessDate;
      this.internalLabelFirst = var1.internalLabelFirst;
      this.lineNumberIncrement = var1.lineNumberIncrement;
      this.options = var1.options;
      this.timeStamp = var1.timeStamp;
      this.baseLibrary = var1.baseLibrary;
   }

   public String getName() {
      return this.name;
   }

   public String getLongName() {
      return this.longName;
   }

   public String getUser() {
      return this.user;
   }

   public String getGpUser() {
      return this.gpUser;
   }

   public String getCodePage() {
      return this.codePage;
   }

   public int getSourceSize() {
      return this.sourceSize;
   }

   public int getGpSize() {
      return this.gpSize;
   }

   public int getKind() {
      return this.kind;
   }

   public int getType() {
      return this.type;
   }

   public int getDatbaseId() {
      return this.databaseId;
   }

   public int getFnr() {
      return this.fnr;
   }

   public boolean isStructured() {
      return this.isStructured;
   }

   public boolean isLinkedDdm() {
      return this.isLinkedDdm;
   }

   public PalDate getSourceDate() {
      return this.sourceDate;
   }

   public PalDate getGpDate() {
      return this.gpDate;
   }

   public PalDate getAccessDate() {
      return this.accessDate;
   }

   public String getInternalLabelFirst() {
      return this.internalLabelFirst;
   }

   public int getLineNumberIncrement() {
      return this.lineNumberIncrement;
   }

   public PalDate getDate() {
      try {
         return this.kind != 1 && this.kind != 16 && this.kind != 64 ? this.gpDate : this.sourceDate;
      } catch (ObjectProperties$NullPointerException var1) {
         return null;
      }
   }

   public int getSize() {
      return this.kind != 1 && this.kind != 16 && this.kind != 64 ? this.gpSize : this.sourceSize;
   }

   public Set getOptions() {
      return this.options;
   }

   public PalTimeStamp getTimeStamp() {
      return this.timeStamp;
   }

   public String getBaseLibrary() {
      return this.baseLibrary;
   }

   // $FF: synthetic method
   ObjectProperties(Builder var1, ObjectProperties var2) {
      this(var1);
   }

   public static class Builder {
      private String name;
      private int type;
      private int gpSize = 0;
      private String longName = "";
      private String user = "";
      private String gpUser = "";
      private String codePage = "";
      private int sourceSize = 0;
      private int natKind = 1;
      private int databaseId = 0;
      private int fnr = 0;
      private boolean isStructured = true;
      private boolean isLinkedDdm = false;
      private PalDate sourceDate = new PalDate();
      private PalDate gpDate = new PalDate();
      private PalDate accessDate = new PalDate();
      private String internalLabelFirst = "";
      private int lineNumberIncrement = 0;
      private Set options;
      private PalTimeStamp timeStamp;
      private String baseLibrary;

      public Builder(String var1, int var2) {
         this.options = EnumSet.of(EFileOptions.INIT);
         this.timeStamp = null;
         this.baseLibrary = null;
         this.name = var1;
         this.type = var2;
      }

      public Builder longName(String var1) {
         try {
            this.longName = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder user(String var1) {
         try {
            this.user = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder gpUser(String var1) {
         try {
            this.gpUser = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder codePage(String var1) {
         try {
            this.codePage = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder sourceSize(int var1) {
         try {
            this.sourceSize = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder gpSize(int var1) {
         try {
            this.gpSize = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder natKind(int var1) {
         try {
            this.natKind = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder databaseId(int var1) {
         try {
            this.databaseId = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder fileNumber(int var1) {
         try {
            this.fnr = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder isStructured(boolean var1) {
         try {
            this.isStructured = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder isLinkedDDm(boolean var1) {
         try {
            this.isLinkedDdm = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder sourceDate(PalDate var1) {
         try {
            this.sourceDate = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder gpDate(PalDate var1) {
         try {
            this.gpDate = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder accessDate(PalDate var1) {
         try {
            this.accessDate = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder internalLabelFirst(String var1) {
         try {
            this.internalLabelFirst = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder lineNumberIncrement(int var1) {
         try {
            this.lineNumberIncrement = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder options(Set var1) {
         try {
            this.options = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder timeStamp(PalTimeStamp var1) {
         try {
            this.timeStamp = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public Builder baseLibrary(String var1) {
         try {
            this.baseLibrary = var1;
            return this;
         } catch (ObjectProperties$NullPointerException var2) {
            return null;
         }
      }

      public ObjectProperties build() {
         try {
            return new ObjectProperties(this, (ObjectProperties)null);
         } catch (ObjectProperties$NullPointerException var1) {
            return null;
         }
      }
   }
}
