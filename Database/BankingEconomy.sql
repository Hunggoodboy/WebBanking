USE [master]
GO
/****** Object:  Database [BankingEconomy]    Script Date: 5/12/26 3:38:25PM ******/
CREATE DATABASE [BankingEconomy]
 CONTAINMENT = NONE
 ON  PRIMARY 
( NAME = N'BankingEconomy', FILENAME = N'/var/opt/mssql/data/BankingEconomy.mdf' , SIZE = 8192KB , MAXSIZE = UNLIMITED, FILEGROWTH = 65536KB )
 LOG ON 
( NAME = N'BankingEconomy_log', FILENAME = N'/var/opt/mssql/data/BankingEconomy_log.ldf' , SIZE = 73728KB , MAXSIZE = 2048GB , FILEGROWTH = 65536KB )
GO
IF (1 = FULLTEXTSERVICEPROPERTY('IsFullTextInstalled'))
begin
EXEC [BankingEconomy].[dbo].[sp_fulltext_database] @action = 'enable'
end
GO
ALTER DATABASE [BankingEconomy] SET ANSI_NULL_DEFAULT OFF 
GO
ALTER DATABASE [BankingEconomy] SET ANSI_NULLS OFF 
GO
ALTER DATABASE [BankingEconomy] SET ANSI_PADDING OFF 
GO
ALTER DATABASE [BankingEconomy] SET ANSI_WARNINGS OFF 
GO
ALTER DATABASE [BankingEconomy] SET ARITHABORT OFF 
GO
ALTER DATABASE [BankingEconomy] SET AUTO_CLOSE OFF 
GO
ALTER DATABASE [BankingEconomy] SET AUTO_SHRINK OFF 
GO
ALTER DATABASE [BankingEconomy] SET AUTO_UPDATE_STATISTICS ON 
GO
ALTER DATABASE [BankingEconomy] SET CURSOR_CLOSE_ON_COMMIT OFF 
GO
ALTER DATABASE [BankingEconomy] SET CURSOR_DEFAULT  GLOBAL 
GO
ALTER DATABASE [BankingEconomy] SET CONCAT_NULL_YIELDS_NULL OFF 
GO
ALTER DATABASE [BankingEconomy] SET NUMERIC_ROUNDABORT OFF 
GO
ALTER DATABASE [BankingEconomy] SET QUOTED_IDENTIFIER OFF 
GO
ALTER DATABASE [BankingEconomy] SET RECURSIVE_TRIGGERS OFF 
GO
ALTER DATABASE [BankingEconomy] SET  ENABLE_BROKER 
GO
ALTER DATABASE [BankingEconomy] SET AUTO_UPDATE_STATISTICS_ASYNC OFF 
GO
ALTER DATABASE [BankingEconomy] SET DATE_CORRELATION_OPTIMIZATION OFF 
GO
ALTER DATABASE [BankingEconomy] SET TRUSTWORTHY OFF 
GO
ALTER DATABASE [BankingEconomy] SET ALLOW_SNAPSHOT_ISOLATION OFF 
GO
ALTER DATABASE [BankingEconomy] SET PARAMETERIZATION SIMPLE 
GO
ALTER DATABASE [BankingEconomy] SET READ_COMMITTED_SNAPSHOT OFF 
GO
ALTER DATABASE [BankingEconomy] SET HONOR_BROKER_PRIORITY OFF 
GO
ALTER DATABASE [BankingEconomy] SET RECOVERY FULL 
GO
ALTER DATABASE [BankingEconomy] SET  MULTI_USER 
GO
ALTER DATABASE [BankingEconomy] SET PAGE_VERIFY CHECKSUM  
GO
ALTER DATABASE [BankingEconomy] SET DB_CHAINING OFF 
GO
ALTER DATABASE [BankingEconomy] SET FILESTREAM( NON_TRANSACTED_ACCESS = OFF ) 
GO
ALTER DATABASE [BankingEconomy] SET TARGET_RECOVERY_TIME = 60 SECONDS 
GO
ALTER DATABASE [BankingEconomy] SET DELAYED_DURABILITY = DISABLED 
GO
EXEC sys.sp_db_vardecimal_storage_format N'BankingEconomy', N'ON'
GO
ALTER DATABASE [BankingEconomy] SET QUERY_STORE = ON
GO
ALTER DATABASE [BankingEconomy] SET QUERY_STORE (OPERATION_MODE = READ_WRITE, CLEANUP_POLICY = (STALE_QUERY_THRESHOLD_DAYS = 30), DATA_FLUSH_INTERVAL_SECONDS = 900, INTERVAL_LENGTH_MINUTES = 60, MAX_STORAGE_SIZE_MB = 1000, QUERY_CAPTURE_MODE = AUTO, SIZE_BASED_CLEANUP_MODE = AUTO)
GO
USE [BankingEconomy]
GO
ALTER DATABASE SCOPED CONFIGURATION SET ACCELERATED_PLAN_FORCING = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET ASYNC_STATS_UPDATE_WAIT_AT_LOW_PRIORITY = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET BATCH_MODE_ADAPTIVE_JOINS = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET BATCH_MODE_MEMORY_GRANT_FEEDBACK = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET BATCH_MODE_ON_ROWSTORE = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET CE_FEEDBACK = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET DEFERRED_COMPILATION_TV = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET DOP_FEEDBACK = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET DW_COMPATIBILITY_LEVEL = 0;
GO
ALTER DATABASE SCOPED CONFIGURATION SET ELEVATE_ONLINE = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET ELEVATE_RESUMABLE = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET EXEC_QUERY_STATS_FOR_SCALAR_FUNCTIONS = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET FORCE_SHOWPLAN_RUNTIME_PARAMETER_COLLECTION = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET GLOBAL_TEMPORARY_TABLE_AUTO_DROP = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET IDENTITY_CACHE = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET INTERLEAVED_EXECUTION_TVF = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET ISOLATE_SECURITY_POLICY_CARDINALITY = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET LAST_QUERY_PLAN_STATS = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET LEDGER_DIGEST_STORAGE_ENDPOINT = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET LEGACY_CARDINALITY_ESTIMATION = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION FOR SECONDARY SET LEGACY_CARDINALITY_ESTIMATION = PRIMARY;
GO
ALTER DATABASE SCOPED CONFIGURATION SET LIGHTWEIGHT_QUERY_PROFILING = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET MAXDOP = 0;
GO
ALTER DATABASE SCOPED CONFIGURATION FOR SECONDARY SET MAXDOP = PRIMARY;
GO
ALTER DATABASE SCOPED CONFIGURATION SET MEMORY_GRANT_FEEDBACK_PERCENTILE_GRANT = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET MEMORY_GRANT_FEEDBACK_PERSISTENCE = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET OPTIMIZED_PLAN_FORCING = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET OPTIMIZE_FOR_AD_HOC_WORKLOADS = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET PARAMETER_SENSITIVE_PLAN_OPTIMIZATION = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET PARAMETER_SNIFFING = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION FOR SECONDARY SET PARAMETER_SNIFFING = PRIMARY;
GO
ALTER DATABASE SCOPED CONFIGURATION SET PAUSED_RESUMABLE_INDEX_ABORT_DURATION_MINUTES = 1440;
GO
ALTER DATABASE SCOPED CONFIGURATION SET QUERY_OPTIMIZER_HOTFIXES = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION FOR SECONDARY SET QUERY_OPTIMIZER_HOTFIXES = PRIMARY;
GO
ALTER DATABASE SCOPED CONFIGURATION SET ROW_MODE_MEMORY_GRANT_FEEDBACK = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET TSQL_SCALAR_UDF_INLINING = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET VERBOSE_TRUNCATION_WARNINGS = ON;
GO
ALTER DATABASE SCOPED CONFIGURATION SET XTP_PROCEDURE_EXECUTION_STATISTICS = OFF;
GO
ALTER DATABASE SCOPED CONFIGURATION SET XTP_QUERY_EXECUTION_STATISTICS = OFF;
GO
USE [BankingEconomy]
GO
/****** Object:  UserDefinedFunction [dbo].[non_accent]    Script Date: 5/12/26 3:38:26PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE FUNCTION [dbo].[non_accent] ( @strInput NVARCHAR(MAX) ) 
RETURNS NVARCHAR(MAX) AS 
BEGIN 
    IF @strInput IS NULL RETURN @strInput 
    IF @strInput = '' RETURN @strInput 

    DECLARE @SIGN_CHARS NCHAR(136) 
    DECLARE @UNSIGN_CHARS NCHAR (136) 

    SET @SIGN_CHARS = N'ăâđêôơưàảãáạăằẳẵắặâầẩẫấậèẻẽéẹêềểễếệìỉĩíịòỏõóọôồổỗốộơờởỡớợùủũúụưừửữứựỳỷỹýỵĂÂĐÊÔƠƯÀẢÃÁẠĂẰẲẴẮẶÂẦẨẪẤẬÈẺẼÉẸÊỀỂỄẾỆÌỈĨÍỊÒỎÕÓỌÔỒỔỐỘƠỜỞỠỚỢÙỦŨÚỤƯỪỬỮỨỰỲỶỸÝỴ' 
    SET @UNSIGN_CHARS = N'aadeoouaaaaaaaaaaaaaaaeeeeeeeeeeiiiiiooooooooooooooouuuuuuuuuuyyyyyAADEOOUAAAAAAAAAAAAAAAEEEEEEEEEEIIIIIOOOOOOOOOOOOOOOUUUUUUUUUUYYYYY' 

    DECLARE @COUNTER int = 1 
    DECLARE @RT NVARCHAR(MAX) = @strInput 

    WHILE (@COUNTER <= LEN(@RT)) 
    BEGIN 
        DECLARE @INDEX int = CHARINDEX(SUBSTRING(@RT,@COUNTER,1),@SIGN_CHARS) 
        IF (@INDEX > 0) 
            SET @RT = STUFF(@RT,@COUNTER,1,SUBSTRING(@UNSIGN_CHARS,@INDEX,1)) 
        SET @COUNTER = @COUNTER + 1 
    END 
    RETURN @RT 
END
GO
/****** Object:  Table [dbo].[accounts]    Script Date: 5/12/26 3:38:26PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[accounts](
	[balance] [float] NOT NULL,
	[created_at] [datetime2](7) NULL,
	[id] [uniqueidentifier] NOT NULL,
	[user_id] [uniqueidentifier] NULL,
	[account_number] [varchar](20) NULL,
	[status] [varchar](255) NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO
/****** Object:  Table [dbo].[audit_logs]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[audit_logs](
	[id] [uniqueidentifier] NOT NULL,
	[amount] [float] NULL,
	[created_at] [datetime2](7) NOT NULL,
	[description] [varchar](255) NULL,
	[fail_reason] [varchar](255) NULL,
	[from_account_number] [varchar](255) NULL,
	[processed_at] [datetime2](7) NULL,
	[status] [varchar](255) NULL,
	[to_account_number] [varchar](255) NULL,
	[transaction_id] [varchar](255) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO
/****** Object:  Table [dbo].[beneficiaries]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[beneficiaries](
	[id] [uniqueidentifier] NOT NULL,
	[target_account_id] [uniqueidentifier] NULL,
	[user_id] [uniqueidentifier] NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO
/****** Object:  Table [dbo].[notifications]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[notifications](
	[is_read] [bit] NULL,
	[created_at] [datetime2](7) NULL,
	[id] [uniqueidentifier] NOT NULL,
	[user_id] [uniqueidentifier] NULL,
	[message] [varchar](20) NULL,
	[type] [varchar](255) NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO
/****** Object:  Table [dbo].[saved_receiver_accounts]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[saved_receiver_accounts](
	[id] [uniqueidentifier] NOT NULL,
	[created_at] [datetime2](7) NOT NULL,
	[updated_at] [datetime2](7) NOT NULL,
	[target_account_id] [uniqueidentifier] NULL,
	[user_id] [uniqueidentifier] NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO
/****** Object:  Table [dbo].[transactions]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[transactions](
	[amount] [float] NOT NULL,
	[created_at] [datetime2](7) NULL,
	[from_account_id] [uniqueidentifier] NULL,
	[id] [uniqueidentifier] NOT NULL,
	[to_account_id] [uniqueidentifier] NULL,
	[status] [varchar](20) NULL,
	[description] [varchar](255) NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO
/****** Object:  Table [dbo].[users]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[users](
	[created_at] [datetime2](7) NOT NULL,
	[id] [uniqueidentifier] NOT NULL,
	[district] [nvarchar](255) NULL,
	[email] [varchar](255) NOT NULL,
	[full_name] [nvarchar](255) NULL,
	[gender] [varchar](255) NULL,
	[identity_card] [varchar](255) NOT NULL,
	[password] [varchar](255) NOT NULL,
	[phone] [varchar](255) NOT NULL,
	[province] [nvarchar](255) NULL,
	[role] [varchar](255) NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UK26bl9ms0sjsk50osog8mhmq5i] UNIQUE NONCLUSTERED 
(
	[identity_card] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY],
 CONSTRAINT [UK6dotkott2kjsp8vw4d0m25fb7] UNIQUE NONCLUSTERED 
(
	[email] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]

GO
/****** Object:  Index [idx_user_id]    Script Date: 5/12/26 3:38:29PM ******/
CREATE NONCLUSTERED INDEX [idx_user_id] ON [dbo].[accounts]
(
	[user_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
/****** Object:  Index [UK6kplolsdtr3slnvx97xsy2kc8]    Script Date: 5/12/26 3:38:29PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [UK6kplolsdtr3slnvx97xsy2kc8] ON [dbo].[accounts]
(
	[account_number] ASC
)
WHERE ([account_number] IS NOT NULL)
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [idx_user_id]    Script Date: 5/12/26 3:38:29PM ******/
CREATE NONCLUSTERED INDEX [idx_user_id] ON [dbo].[beneficiaries]
(
	[user_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [idx_user_id]    Script Date: 5/12/26 3:38:29PM ******/
CREATE NONCLUSTERED INDEX [idx_user_id] ON [dbo].[notifications]
(
	[user_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
/****** Object:  Index [idx_user_type]    Script Date: 5/12/26 3:38:29PM ******/
CREATE NONCLUSTERED INDEX [idx_user_type] ON [dbo].[notifications]
(
	[user_id] ASC,
	[type] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [idx_saved_receiver_target_account_id]    Script Date: 5/12/26 3:38:29PM ******/
CREATE NONCLUSTERED INDEX [idx_saved_receiver_target_account_id] ON [dbo].[saved_receiver_accounts]
(
	[target_account_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [idx_saved_receiver_user_id]    Script Date: 5/12/26 3:38:29PM ******/
CREATE NONCLUSTERED INDEX [idx_saved_receiver_user_id] ON [dbo].[saved_receiver_accounts]
(
	[user_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [uk_saved_receiver_user_target_account]    Script Date: 5/12/26 3:38:29PM ******/
CREATE UNIQUE NONCLUSTERED INDEX [uk_saved_receiver_user_target_account] ON [dbo].[saved_receiver_accounts]
(
	[user_id] ASC,
	[target_account_id] ASC
)
WHERE ([user_id] IS NOT NULL AND [target_account_id] IS NOT NULL)
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [idx_from_and_to_account_id]    Script Date: 5/12/26 3:38:29PM ******/
CREATE NONCLUSTERED INDEX [idx_from_and_to_account_id] ON [dbo].[transactions]
(
	[from_account_id] ASC,
	[to_account_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
/****** Object:  Index [idx_to_account_id]    Script Date: 5/12/26 3:38:29PM ******/
CREATE NONCLUSTERED INDEX [idx_to_account_id] ON [dbo].[transactions]
(
	[to_account_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD  CONSTRAINT [FKnjuop33mo69pd79ctplkck40n] FOREIGN KEY([user_id])
REFERENCES [dbo].[users] ([id])
GO
ALTER TABLE [dbo].[accounts] CHECK CONSTRAINT [FKnjuop33mo69pd79ctplkck40n]
GO
ALTER TABLE [dbo].[beneficiaries]  WITH CHECK ADD  CONSTRAINT [FK2wofakjucayu2rptpe35iy4kq] FOREIGN KEY([target_account_id])
REFERENCES [dbo].[accounts] ([id])
GO
ALTER TABLE [dbo].[beneficiaries] CHECK CONSTRAINT [FK2wofakjucayu2rptpe35iy4kq]
GO
ALTER TABLE [dbo].[beneficiaries]  WITH CHECK ADD  CONSTRAINT [FKk8iehn8e7itlnc8pev97p1bty] FOREIGN KEY([user_id])
REFERENCES [dbo].[users] ([id])
GO
ALTER TABLE [dbo].[beneficiaries] CHECK CONSTRAINT [FKk8iehn8e7itlnc8pev97p1bty]
GO
ALTER TABLE [dbo].[notifications]  WITH CHECK ADD  CONSTRAINT [FK9y21adhxn0ayjhfocscqox7bh] FOREIGN KEY([user_id])
REFERENCES [dbo].[users] ([id])
GO
ALTER TABLE [dbo].[notifications] CHECK CONSTRAINT [FK9y21adhxn0ayjhfocscqox7bh]
GO
ALTER TABLE [dbo].[saved_receiver_accounts]  WITH CHECK ADD  CONSTRAINT [FK1dy91x3iot7m3qqunmgw58r7k] FOREIGN KEY([target_account_id])
REFERENCES [dbo].[accounts] ([id])
GO
ALTER TABLE [dbo].[saved_receiver_accounts] CHECK CONSTRAINT [FK1dy91x3iot7m3qqunmgw58r7k]
GO
ALTER TABLE [dbo].[saved_receiver_accounts]  WITH CHECK ADD  CONSTRAINT [FKluo00rjfittv0cbaqb4y1ewb8] FOREIGN KEY([user_id])
REFERENCES [dbo].[users] ([id])
GO
ALTER TABLE [dbo].[saved_receiver_accounts] CHECK CONSTRAINT [FKluo00rjfittv0cbaqb4y1ewb8]
GO
ALTER TABLE [dbo].[transactions]  WITH CHECK ADD  CONSTRAINT [FK7i7kboanveneetad7jyhbr0a7] FOREIGN KEY([from_account_id])
REFERENCES [dbo].[accounts] ([id])
GO
ALTER TABLE [dbo].[transactions] CHECK CONSTRAINT [FK7i7kboanveneetad7jyhbr0a7]
GO
ALTER TABLE [dbo].[transactions]  WITH CHECK ADD  CONSTRAINT [FKra0an432c5wjo76mojluk0v28] FOREIGN KEY([to_account_id])
REFERENCES [dbo].[accounts] ([id])
GO
ALTER TABLE [dbo].[transactions] CHECK CONSTRAINT [FKra0an432c5wjo76mojluk0v28]
GO
ALTER TABLE [dbo].[accounts]  WITH CHECK ADD CHECK  (([status]='SUSPENDED' OR [status]='INACTIVE' OR [status]='ACTIVE'))
GO
ALTER TABLE [dbo].[notifications]  WITH CHECK ADD CHECK  (([type]='ALERT' OR [type]='MARKETING' OR [type]='TRANSACTION'))
GO
/****** Object:  StoredProcedure [dbo].[sp_chuyentien]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE PROCEDURE [dbo].[sp_chuyentien]
	    @FromAccountID UNIQUEIDENTIFIER,
	    @ToAccountID UNIQUEIDENTIFIER
	AS
	BEGIN
	    -- Tắt thông báo số dòng ảnh hưởng
	    SET NOCOUNT ON;
	
	    -- Hoàn thiện câu lệnh SELECT và bọc tên bảng bằng ngoặc vuông
	    SELECT *
	    FROM [transaction]
	    WHERE from_account_id = @FromAccountID
	      AND to_account_id = @ToAccountID;
	END
GO
/****** Object:  Trigger [dbo].[checkaccount]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
create trigger [dbo].[checkaccount]
on [dbo].[transactions]
for insert, update
as
begin
	begin transaction
		UPDATE account
		Set amount = 50000
		where from_account_id = 12345 and amount >= 100000 
	commit transaction
end
GO
ALTER TABLE [dbo].[transactions] ENABLE TRIGGER [checkaccount]
GO
/****** Object:  Trigger [dbo].[checkaccounts]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
create trigger [dbo].[checkaccounts]
on [dbo].[transactions]
for insert, update
as
begin
	begin transaction
		UPDATE account
		Set amount = amount - 50000
		where from_account_id = 123456789 and amount >= 100000
		
		UPDATE account
		Set amount = amount + 50000
		where to_account_id = 1245325435
	commit transaction
end
GO
ALTER TABLE [dbo].[transactions] ENABLE TRIGGER [checkaccounts]
GO
/****** Object:  Trigger [dbo].[tgTransaction]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE trigger [dbo].[tgTransaction]
ON [dbo].[transactions]
for insert, update
as
begin
	IF EXISTS(
		select 1
		from inserted
		where status = 'PENDING'
	)
	BEGIN
		RAISERROR(N'Loi', 16,1);
	END
	
end
GO
ALTER TABLE [dbo].[transactions] ENABLE TRIGGER [tgTransaction]
GO
/****** Object:  Trigger [dbo].[trg_view]    Script Date: 5/12/26 3:38:29PM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
Create Trigger [dbo].[trg_view]
ON [dbo].[users]
For INSERT, Update
As
Begin
	IF exists (
	SELECT 1
	From users
	Where province = null
	)
	Begin
		RAISERROR (N'Lỗi : người dùng chưa điền tỉnh', 16, 1);
	END
END
GO
ALTER TABLE [dbo].[users] ENABLE TRIGGER [trg_view]
GO
USE [master]
GO
ALTER DATABASE [BankingEconomy] SET  READ_WRITE 
GO
