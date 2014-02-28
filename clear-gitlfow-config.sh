#!/bin/sh

git config --unset-all "gitflow.branch.master"
git config --unset-all "gitflow.branch.develop"
git config --unset-all "gitflow.prefix.feature"
git config --unset-all "gitflow.prefix.release"
git config --unset-all "gitflow.prefix.hotfix"
git config --unset-all "gitflow.prefix.support"
git config --unset-all "gitflow.prefix.versiontag"

git config -l
