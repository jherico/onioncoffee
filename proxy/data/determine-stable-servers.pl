#!/usr/bin/perl -w

use strict;

my $reliable = 0.95;
my $output = "directory-stable";

die("give me a list of directory files as parameter!") unless(@ARGV);

undef $/;   # activate slurp-mode

my %server;
my $dir_count = 0;

foreach my $p (@ARGV) {
	foreach my $dirfile (glob($p)) {
		++$dir_count;
		# read directory from file
		open(DIR,"< $dirfile") || die("can't read $dirfile");
		my $curr_dir = <DIR>;
		close(DIR);
		# extract router-status
		my ($router_status) = ($curr_dir=~/^router-status (.*?)$/smi);
		warn("no router status in $dirfile") unless($router_status);
		# parse for dir-servers
		while($curr_dir =~ /^(router\s+(\S+)\s+(\S+)\s+.*?\n\n)/gsmi) {
			my ($description,$nick,$ip) = ($1,$2,$3);
			unless(defined $server{$nick}) {
				my $info = { counts => 0, ip=>$ip, descr=>$description, changes=>0, trusted=>0 };
				$server{$nick} = $info;
			} else {
				# update descriptor, if current descriptor is newer
				my ($old_published) = ($server{$nick}{descr} =~ /^published (.*?)$/smi);
				my ($new_published) = ($description =~ /^published (.*?)$/smi);
				if (($new_published cmp $old_published)>=0) {
					$server{$nick}{descr} = $description;
					#} else {
					#warn("description for $nick in $dirfile is not uptodate");
				}
			}
			# do statistics
			$server{$nick}{counts} += 1;
			if ($router_status =~ / $nick=\$([0-9a-fA-F]+)\b/s) {   # name=$digest   [Verified router, currently live.]
				$server{$nick}{trusted} += 1;
				$server{$nick}{digest} = $1;
			}
			if (!($server{$nick}{ip} eq $ip)) {
				$server{$nick}{ip} = $ip;
				$server{$nick}{changes} += 1;
			}
		}
	}
}

my @stable_keys = grep {$server{$_}{counts} >= $dir_count*$reliable } keys(%server);

my @fix_keys = grep {$server{$_}{changes} <= $dir_count*(1-$reliable) } @stable_keys;

#my @sort_changes = sort {$server{$a}{changes} <=> $server{$b}{changes}} @fix_keys;
my @sort_nicks = sort {$a cmp $b} @fix_keys;

my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst) = localtime(time);
my $timestamp = sprintf("%04d-%02d-%02d %02d:%02d:%02d",$year+1900,$mon+1,$mday,$hour,$min,$sec);

# output stable server into directory
open(OUT,"> $output") || die("can't write to $output");
#print OUT "signed-directory\n";
print OUT "published $timestamp\n";
print OUT "router-status";
foreach my $nick (@sort_nicks) {
	if ($server{$nick}{trusted} >= $dir_count*$reliable) {
		print OUT " $nick=\$",$server{$nick}{digest};
	}
}
print OUT "\n\n";

# print directory stuff
foreach my $nick (@sort_nicks) {
	printf("%s appeared %d times, %d times trusted and changed %d times\n",$nick,$server{$nick}{counts},$server{$nick}{trusted},$server{$nick}{changes});
	print OUT $server{$nick}{descr};
}
close(OUT);
