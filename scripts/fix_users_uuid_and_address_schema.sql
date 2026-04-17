BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH('users', 'province') IS NULL
    BEGIN
        ALTER TABLE users
        ADD province VARCHAR(255) NOT NULL
            CONSTRAINT DF_users_province DEFAULT '';
    END;

    IF COL_LENGTH('users', 'district') IS NULL
    BEGIN
        ALTER TABLE users
        ADD district VARCHAR(255) NOT NULL
            CONSTRAINT DF_users_district DEFAULT '';
    END;

    UPDATE users
    SET role = 'CUSTOMER'
    WHERE role IS NULL OR role IN ('User', 'user');

    IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK__users__role__412EB0B6')
        ALTER TABLE users DROP CONSTRAINT CK__users__role__412EB0B6;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.check_constraints
        WHERE parent_object_id = OBJECT_ID('users')
          AND definition LIKE '%CUSTOMER%'
          AND definition LIKE '%ADMIN%'
    )
    BEGIN
        ALTER TABLE users
        ADD CONSTRAINT CK_users_role
            CHECK (role IN ('ADMIN', 'CUSTOMER'));
    END;

    IF EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = 'users'
          AND COLUMN_NAME = 'id'
          AND DATA_TYPE <> 'uniqueidentifier'
    )
    BEGIN
        ALTER TABLE users ADD id_uuid UNIQUEIDENTIFIER NULL;
        UPDATE users SET id_uuid = NEWID() WHERE id_uuid IS NULL;

        IF COL_LENGTH('accounts', 'user_uuid') IS NULL
            ALTER TABLE accounts ADD user_uuid UNIQUEIDENTIFIER NULL;

        IF COL_LENGTH('beneficiaries', 'user_uuid') IS NULL
            ALTER TABLE beneficiaries ADD user_uuid UNIQUEIDENTIFIER NULL;

        IF COL_LENGTH('notifications', 'user_uuid') IS NULL
            ALTER TABLE notifications ADD user_uuid UNIQUEIDENTIFIER NULL;

        UPDATE a
        SET a.user_uuid = u.id_uuid
        FROM accounts a
        JOIN users u ON a.user_id = u.id;

        UPDATE b
        SET b.user_uuid = u.id_uuid
        FROM beneficiaries b
        JOIN users u ON b.user_id = u.id;

        UPDATE n
        SET n.user_uuid = u.id_uuid
        FROM notifications n
        JOIN users u ON n.user_id = u.id;

        IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FKnjuop33mo69pd79ctplkck40n')
            ALTER TABLE accounts DROP CONSTRAINT FKnjuop33mo69pd79ctplkck40n;

        IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FKk8iehn8e7itlnc8pev97p1bty')
            ALTER TABLE beneficiaries DROP CONSTRAINT FKk8iehn8e7itlnc8pev97p1bty;

        IF EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK9y21adhxn0ayjhfocscqox7bh')
            ALTER TABLE notifications DROP CONSTRAINT FK9y21adhxn0ayjhfocscqox7bh;

        IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_user_id' AND object_id = OBJECT_ID('accounts'))
            DROP INDEX idx_user_id ON accounts;

        IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_user_id' AND object_id = OBJECT_ID('beneficiaries'))
            DROP INDEX idx_user_id ON beneficiaries;

        IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_notif_user_id' AND object_id = OBJECT_ID('notifications'))
            DROP INDEX idx_notif_user_id ON notifications;

        IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_notif_user_type' AND object_id = OBJECT_ID('notifications'))
            DROP INDEX idx_notif_user_type ON notifications;

        IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_user_id' AND object_id = OBJECT_ID('notifications'))
            DROP INDEX idx_user_id ON notifications;

        IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_user_type' AND object_id = OBJECT_ID('notifications'))
            DROP INDEX idx_user_type ON notifications;

        ALTER TABLE accounts DROP COLUMN user_id;
        EXEC sp_rename 'accounts.user_uuid', 'user_id', 'COLUMN';

        ALTER TABLE beneficiaries DROP COLUMN user_id;
        EXEC sp_rename 'beneficiaries.user_uuid', 'user_id', 'COLUMN';

        ALTER TABLE notifications DROP COLUMN user_id;
        EXEC sp_rename 'notifications.user_uuid', 'user_id', 'COLUMN';

        IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'PK__users__3213E83F087ACB8F')
            ALTER TABLE users DROP CONSTRAINT PK__users__3213E83F087ACB8F;

        ALTER TABLE users DROP COLUMN id;
        EXEC sp_rename 'users.id_uuid', 'id', 'COLUMN';
        ALTER TABLE users ALTER COLUMN id UNIQUEIDENTIFIER NOT NULL;
        ALTER TABLE users ADD CONSTRAINT PK_users PRIMARY KEY (id);

        CREATE INDEX idx_user_id ON accounts(user_id);
        CREATE INDEX idx_user_id ON beneficiaries(user_id);
        CREATE INDEX idx_user_id ON notifications(user_id);
        CREATE INDEX idx_user_type ON notifications(user_id, type);

        ALTER TABLE accounts
        ADD CONSTRAINT FK_accounts_users
            FOREIGN KEY (user_id) REFERENCES users(id);

        ALTER TABLE beneficiaries
        ADD CONSTRAINT FK_beneficiaries_users
            FOREIGN KEY (user_id) REFERENCES users(id);

        ALTER TABLE notifications
        ADD CONSTRAINT FK_notifications_users
            FOREIGN KEY (user_id) REFERENCES users(id);
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    THROW;
END CATCH;
