package com.bogdan.starter.db

object DbQueries {
    const val SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)"
    const val SQL_GET_PAGE = "select Id, Content from Pages where Name = ?"
    const val SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)"
    const val SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?"
    const val SQL_ALL_PAGES = "select Name from Pages"
    const val SQL_DELETE_PAGE = "delete from Pages where Id = ?"
}