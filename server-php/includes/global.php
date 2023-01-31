<?php
defined ('main') or die ( 'Access Denied' );

//DB- Connection
//------------------------------------------------------------------------------//
require_once ('_mysql.inc');
$GLOBAL_SQL_Con = new mysqli($sql_host,$sql_user,$sql_password,$sql_database, $sql_port);

if ($GLOBAL_SQL_Con->connect_error) {
    echo 'Connect Error (' . $GLOBAL_SQL_Con->connect_errno . ') '. $GLOBAL_SQL_Con->connect_error;
}
if (!$GLOBAL_SQL_Con->set_charset("utf8")) {
    echo "Error loading character set utf8: ".$GLOBAL_SQL_Con->error;
}

//$GLOBAL_PAGE_URL = "http://".$_SERVER[HTTP_HOST]."/www/";

$GLOBAL_TBL_STUDY1 = "se_data_one";


//-------------------------------------------------------------------------//
//require_once ($root.'includes/function.php');
?>