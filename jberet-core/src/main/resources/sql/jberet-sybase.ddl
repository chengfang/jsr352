CREATE TABLE JOB_INSTANCE (
  JOBINSTANCEID   BIGINT IDENTITY PRIMARY KEY,
  JOBNAME         VARCHAR(512),
  APPLICATIONNAME VARCHAR(512)
)!
CREATE TABLE JOB_EXECUTION (
  JOBEXECUTIONID  BIGINT IDENTITY PRIMARY KEY,
  JOBINSTANCEID   BIGINT NOT NULL,
  CREATETIME      DATETIME,
  STARTTIME       DATETIME,
  ENDTIME         DATETIME,
  LASTUPDATEDTIME DATETIME,
  BATCHSTATUS     VARCHAR(30),
  EXITSTATUS      VARCHAR(512),
  JOBPARAMETERS   VARCHAR(3000),
  RESTARTPOSITION VARCHAR(255),
  FOREIGN KEY (JOBINSTANCEID) REFERENCES JOB_INSTANCE (JOBINSTANCEID)
)!
CREATE TABLE STEP_EXECUTION (
  STEPEXECUTIONID    BIGINT IDENTITY PRIMARY KEY,
  JOBEXECUTIONID     BIGINT NOT NULL,
  STEPNAME           VARCHAR(255),
  STARTTIME          DATETIME,
  ENDTIME            DATETIME,
  BATCHSTATUS        VARCHAR(30),
  EXITSTATUS         VARCHAR(512),
  EXECUTIONEXCEPTION VARCHAR(5120),
  PERSISTENTUSERDATA VARBINARY(16384),
  READCOUNT          INTEGER,
  WRITECOUNT         INTEGER,
  COMMITCOUNT        INTEGER,
  ROLLBACKCOUNT      INTEGER,
  READSKIPCOUNT      INTEGER,
  PROCESSSKIPCOUNT   INTEGER,
  FILTERCOUNT        INTEGER,
  WRITESKIPCOUNT     INTEGER,
  READERCHECKPOINTINFO  VARBINARY(16384),
  WRITERCHECKPOINTINFO  VARBINARY(16384),
  FOREIGN KEY (JOBEXECUTIONID) REFERENCES JOB_EXECUTION (JOBEXECUTIONID)
)!
CREATE TABLE PARTITION_EXECUTION (
  PARTITIONEXECUTIONID  INTEGER NOT NULL,
  STEPEXECUTIONID       BIGINT  NOT NULL,
  BATCHSTATUS           VARCHAR(30),
  EXITSTATUS            VARCHAR(512),
  EXECUTIONEXCEPTION    VARCHAR(5120),
  PERSISTENTUSERDATA    VARBINARY(16384),
  READERCHECKPOINTINFO  VARBINARY(16384),
  WRITERCHECKPOINTINFO  VARBINARY(16384),
  PRIMARY KEY (PARTITIONEXECUTIONID, STEPEXECUTIONID),
  FOREIGN KEY (STEPEXECUTIONID) REFERENCES STEP_EXECUTION (STEPEXECUTIONID)
)!
